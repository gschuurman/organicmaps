package app.organicmaps.automotive;

import android.location.Location;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.DefaultLifecycleObserver;
import androidx.lifecycle.LifecycleOwner;
import app.organicmaps.MwmActivity;
import app.organicmaps.MwmApplication;
import app.organicmaps.R;
import app.organicmaps.sdk.Framework;
import app.organicmaps.sdk.location.LocationHelper;
import app.organicmaps.sdk.location.LocationListener;
import app.organicmaps.sdk.util.StringUtils;
import app.organicmaps.util.UiUtils;
import app.organicmaps.util.Utils;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * Android Automotive OS, like Google Maps there: the search pill on the map with the home address and the latest
 * places navigated to below it, each with the travel time and distance by car. Tapping a place builds the route to
 * it.
 */
public final class CarPlacesPanel implements DefaultLifecycleObserver, LocationListener
{
  // Re-estimate once the car has moved this far from where the travel times were estimated.
  private static final float REESTIMATE_DISTANCE_METERS = 500;

  private record Estimate(double seconds, double meters)
  {
  }

  @NonNull
  private final MwmActivity mActivity;
  @NonNull
  private final LocationHelper mLocationHelper;
  // Opens the search, see SearchWheel.
  @NonNull
  private final View mSearchButton;
  @NonNull
  private final LinearLayout mList;
  @NonNull
  private final ImageView mToggle;
  private boolean mExpanded = true;
  private int mMaxItems = Integer.MAX_VALUE;
  @Nullable
  private CarPlaces.Place mHome;
  @NonNull
  private List<CarPlaces.Place> mRecents = Collections.emptyList();

  // Only successful estimates, the straight-line distance is shown for the others.
  private final Map<CarPlaces.Place, Estimate> mEstimates = new IdentityHashMap<>();
  @Nullable
  private Location mEstimatedFrom;
  // Results of an older round of estimates are dropped.
  private int mEstimatesRound;
  // Of the current round. Moving on only starts a new round once it is done, the routes are built one at a time.
  private int mPendingEstimates;
  private boolean mDestroyed;

  /** Binds the panel if the map buttons layout has it (map_buttons_layout_car_regular.xml). */
  public static void bindIfPresent(@NonNull View frame, @NonNull Fragment fragment)
  {
    final View panel = frame.findViewById(R.id.car_places_panel);
    if (panel != null)
      new CarPlacesPanel(panel, frame.findViewById(R.id.btn_search), fragment);
  }

  private CarPlacesPanel(@NonNull View panel, @NonNull View searchButton, @NonNull Fragment fragment)
  {
    mActivity = (MwmActivity) fragment.requireActivity();
    mLocationHelper = MwmApplication.from(mActivity).getLocationHelper();
    mSearchButton = searchButton;
    mList = panel.findViewById(R.id.car_places_list);
    mToggle = panel.findViewById(R.id.car_places_toggle);
    panel.findViewById(R.id.car_search_pill).setOnClickListener(v -> mSearchButton.performClick());
    mToggle.setOnClickListener(v -> {
      mExpanded = !mExpanded;
      updateList();
    });

    final LifecycleOwner owner = fragment.getViewLifecycleOwner();
    owner.getLifecycle().addObserver(this);
    CarPlaces.getChanges().observe(owner, changes -> {
      mHome = CarPlaces.getHome(mActivity);
      mRecents = CarPlaces.getRecents(mActivity);
      mEstimates.clear();
      estimateTravel(mLocationHelper.getSavedLocation());
      updateList();
    });
    DrivingRestrictions.get().observe(owner, restrictions -> {
      mMaxItems = restrictions.maxListItems;
      updateList();
    });
  }

  @Override
  public void onStart(@NonNull LifecycleOwner owner)
  {
    mLocationHelper.addListener(this);
    updateList();
  }

  @Override
  public void onStop(@NonNull LifecycleOwner owner)
  {
    mLocationHelper.removeListener(this);
  }

  @Override
  public void onDestroy(@NonNull LifecycleOwner owner)
  {
    mDestroyed = true;
  }

  // The distances change while driving.
  @Override
  public void onLocationUpdated(@NonNull Location location)
  {
    if (mPendingEstimates == 0
        && (mEstimatedFrom == null || mEstimatedFrom.distanceTo(location) > REESTIMATE_DISTANCE_METERS))
      estimateTravel(location);
    updateList();
  }

  // Each estimate builds a route in the background, see RoutingManager::EstimateCarTravel().
  private void estimateTravel(@Nullable Location location)
  {
    if (location == null)
      return;
    mEstimatedFrom = location;
    final int round = ++mEstimatesRound;
    final List<CarPlaces.Place> places = new ArrayList<>();
    if (mHome != null)
      places.add(mHome);
    places.addAll(mRecents);
    mPendingEstimates = places.size();
    for (CarPlaces.Place place : places)
      Framework.nativeEstimateCarTravel(location.getLatitude(), location.getLongitude(), place.lat, place.lon,
                                        (seconds, meters) -> onTravelEstimated(round, place, seconds, meters));
  }

  private void onTravelEstimated(int round, @NonNull CarPlaces.Place place, double seconds, double meters)
  {
    if (mDestroyed || round != mEstimatesRound)
      return;
    --mPendingEstimates;
    if (seconds < 0)
      mEstimates.remove(place);
    else
      mEstimates.put(place, new Estimate(seconds, meters));
    updateList();
  }

  private void updateList()
  {
    mToggle.setImageResource(mExpanded ? R.drawable.ic_expand_less : R.drawable.ic_expand_more);
    UiUtils.showIf(mExpanded, mList);
    mList.removeAllViews();
    if (!mExpanded || mMaxItems == 0)
      return;

    final Location location = mLocationHelper.getSavedLocation();
    final CarPlaces.Place home = mHome;
    if (home == null)
      addRow(R.drawable.ic_home, mActivity.getString(R.string.car_home), "",
             mActivity.getString(R.string.car_home_search), v -> mSearchButton.performClick());
    else
      addPlaceRow(R.drawable.ic_home, mActivity.getString(R.string.car_home), home, location);

    for (int i = 0; i < mRecents.size() && i + 1 < mMaxItems; i++)
    {
      final CarPlaces.Place place = mRecents.get(i);
      addPlaceRow(R.drawable.ic_search_recent, place.name, place, location);
    }
  }

  // Like Google Maps: "10 min · 2.8 km", the travel time in the accent colour.
  private void addPlaceRow(@DrawableRes int icon, @NonNull String title, @NonNull CarPlaces.Place place,
                           @Nullable Location location)
  {
    final Estimate estimate = mEstimates.get(place);
    final CharSequence time;
    final String distance;
    if (estimate != null)
    {
      time = Utils.formatRoutingTime(mActivity, (int) estimate.seconds(), R.dimen.text_size_car_body,
                                     R.dimen.text_size_car_body);
      distance = " · " + StringUtils.nativeFormatDistance(estimate.meters()).toString(mActivity);
    }
    else
    {
      // Until the route is estimated, or when there is none.
      time = "";
      distance = straightDistanceTo(place, location);
    }
    addRow(icon, title, time, distance, v -> mActivity.startLocationToPoint(place.toMapObject()));
  }

  private void addRow(@DrawableRes int icon, @NonNull String title, @NonNull CharSequence time,
                      @NonNull String subtitle, @NonNull View.OnClickListener onClick)
  {
    final View row = LayoutInflater.from(mActivity).inflate(R.layout.item_car_place, mList, false);
    row.<ImageView>findViewById(R.id.icon).setImageResource(icon);
    row.<TextView>findViewById(R.id.title).setText(title);
    final TextView timeView = row.findViewById(R.id.time);
    timeView.setText(time);
    UiUtils.showIf(time.length() > 0, timeView);
    final TextView subtitleView = row.findViewById(R.id.subtitle);
    subtitleView.setText(subtitle);
    UiUtils.showIf(!subtitle.isEmpty(), subtitleView);
    row.setOnClickListener(onClick);
    mList.addView(row);
  }

  @NonNull
  private String straightDistanceTo(@NonNull CarPlaces.Place place, @Nullable Location location)
  {
    if (location == null)
      return "";
    return Framework
        .nativeGetDistanceAndAzimuthFromLatLon(place.lat, place.lon, location.getLatitude(), location.getLongitude(),
                                               0.0)
        .getDistance()
        .toString(mActivity);
  }
}
