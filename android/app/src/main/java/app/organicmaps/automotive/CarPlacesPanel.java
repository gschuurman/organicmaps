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
import app.organicmaps.util.UiUtils;
import java.util.Collections;
import java.util.List;

/**
 * Android Automotive OS, like Google Maps there: the search pill on the map with the home address and the latest
 * places navigated to below it, each with its distance. Tapping a place builds the route to it.
 */
public final class CarPlacesPanel implements DefaultLifecycleObserver, LocationListener
{
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

  // The distances change while driving.
  @Override
  public void onLocationUpdated(@NonNull Location location)
  {
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
      addRow(R.drawable.ic_home, mActivity.getString(R.string.car_home), mActivity.getString(R.string.car_home_search),
             v -> mSearchButton.performClick());
    else
      addRow(R.drawable.ic_home, mActivity.getString(R.string.car_home), distanceTo(home, location),
             v -> mActivity.startLocationToPoint(home.toMapObject()));

    for (int i = 0; i < mRecents.size() && i + 1 < mMaxItems; i++)
    {
      final CarPlaces.Place place = mRecents.get(i);
      addRow(R.drawable.ic_search_recent, place.name, distanceTo(place, location),
             v -> mActivity.startLocationToPoint(place.toMapObject()));
    }
  }

  private void addRow(@DrawableRes int icon, @NonNull String title, @NonNull String subtitle,
                      @NonNull View.OnClickListener onClick)
  {
    final View row = LayoutInflater.from(mActivity).inflate(R.layout.item_car_place, mList, false);
    row.<ImageView>findViewById(R.id.icon).setImageResource(icon);
    row.<TextView>findViewById(R.id.title).setText(title);
    final TextView subtitleView = row.findViewById(R.id.subtitle);
    subtitleView.setText(subtitle);
    UiUtils.showIf(!subtitle.isEmpty(), subtitleView);
    row.setOnClickListener(onClick);
    mList.addView(row);
  }

  // Straight-line distance: a travel time would need a route built for every place.
  @NonNull
  private String distanceTo(@NonNull CarPlaces.Place place, @Nullable Location location)
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
