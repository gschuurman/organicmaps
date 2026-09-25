package app.organicmaps.automotive;

import android.content.Intent;
import android.location.Location;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.Lifecycle;
import app.organicmaps.MwmActivity;
import app.organicmaps.MwmApplication;
import app.organicmaps.R;
import app.organicmaps.bookmarks.BookmarkCategoriesActivity;
import app.organicmaps.sdk.DownloadResourcesLegacyActivity;
import app.organicmaps.sdk.Framework;
import app.organicmaps.sdk.Map;
import app.organicmaps.sdk.MapController;
import app.organicmaps.sdk.MapRenderingListener;
import app.organicmaps.sdk.MapView;
import app.organicmaps.sdk.Router;
import app.organicmaps.sdk.display.DisplayChangedListener;
import app.organicmaps.sdk.display.DisplayManager;
import app.organicmaps.sdk.display.DisplayType;
import app.organicmaps.sdk.location.LocationListener;
import app.organicmaps.sdk.location.LocationState;
import app.organicmaps.sdk.location.LocationUtils;
import app.organicmaps.sdk.routing.RoutingController;
import app.organicmaps.sdk.routing.RoutingInfo;
import app.organicmaps.sdk.util.Config;
import app.organicmaps.sdk.util.log.Logger;
import app.organicmaps.util.UiUtils;
import app.organicmaps.util.Utils;
import java.io.IOException;

/**
 * Android Automotive OS map provider: the map card that the car launcher embeds on its home screen
 * (ACTION_MAIN + CATEGORY_APP_MAPS). It owns the map as the "car" display, handing the renderer over
 * to MwmActivity (the "device" display) whenever the full app is opened, and back when the card is
 * shown again.
 */
public class MapCardActivity
    extends AppCompatActivity implements DisplayChangedListener, MapRenderingListener, LocationListener
{
  private static final String TAG = MapCardActivity.class.getSimpleName();
  private static final int CARD_MIN_ZOOM = 15;
  private static final int CARD_ZOOM = 16;

  @SuppressWarnings("NotNullFieldNotInitialized")
  @NonNull
  private MapView mMapView;
  @SuppressWarnings("NotNullFieldNotInitialized")
  @NonNull
  private View mPlaceholder;
  @SuppressWarnings("NotNullFieldNotInitialized")
  @NonNull
  private View mSearchBar;
  @SuppressWarnings("NotNullFieldNotInitialized")
  @NonNull
  private View mNavBanner;
  @SuppressWarnings("NotNullFieldNotInitialized")
  @NonNull
  private ImageView mNavTurn;
  @SuppressWarnings("NotNullFieldNotInitialized")
  @NonNull
  private TextView mNavTurnDistance;
  @SuppressWarnings("NotNullFieldNotInitialized")
  @NonNull
  private TextView mNavNextStreet;
  @SuppressWarnings("NotNullFieldNotInitialized")
  @NonNull
  private TextView mNavArrival;
  @SuppressWarnings("NotNullFieldNotInitialized")
  @NonNull
  private TextView mNavRemaining;
  @Nullable
  private DisplayManager mDisplayManager;
  @Nullable
  private MapController mMapController;
  // Zoom to the car's position on the first fix after the map is shown (see followMyPosition()).
  private boolean mFollowPending;
  private boolean mMapShown;

  @Override
  protected void onCreate(@Nullable Bundle savedInstanceState)
  {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_map_card);
    mMapView = findViewById(R.id.map_view);
    mPlaceholder = findViewById(R.id.placeholder);
    mSearchBar = findViewById(R.id.search_bar);
    mNavBanner = findViewById(R.id.nav_banner);
    mNavTurn = findViewById(R.id.nav_turn);
    mNavTurnDistance = findViewById(R.id.nav_turn_distance);
    mNavNextStreet = findViewById(R.id.nav_next_street);
    mNavArrival = findViewById(R.id.nav_arrival);
    mNavRemaining = findViewById(R.id.nav_remaining);
    mNavBanner.setOnClickListener(v -> openApp(null));
    findViewById(R.id.touch_overlay).setOnClickListener(v -> openApp(null));
    findViewById(R.id.search).setOnClickListener(v -> openApp(MwmActivity.EXTRA_SHOW_SEARCH));
    findViewById(R.id.bookmarks)
        .setOnClickListener(
            v
            -> startActivity(
                new Intent(this, BookmarkCategoriesActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)));

    final MwmApplication app = MwmApplication.from(this);
    if (app.getOrganicMaps().arePlatformAndCoreInitialized())
    {
      onCoreInitialized();
      return;
    }
    try
    {
      if (!app.initOrganicMaps(this::onCoreInitialized))
        onCoreInitialized();
    }
    catch (IOException e)
    {
      Logger.e(TAG, "Failed to initialize the core", e);
    }
  }

  private void onCoreInitialized()
  {
    if (isDestroyed() || mDisplayManager != null)
      return;
    // The first run (location permission, world map download) needs the full app: keep the logo until then.
    if (Config.isFirstLaunch(this) || !LocationUtils.checkLocationPermission(this)
        || DownloadResourcesLegacyActivity.nativeGetBytesToDownload() != 0)
    {
      Logger.i(TAG, "First run is not finished, the map card waits for the full app");
      return;
    }

    mMapController = new MapController(mMapView, MwmApplication.from(this).getLocationHelper(), this, null, false);
    getLifecycle().addObserver(mMapController);
    mDisplayManager = MwmApplication.from(this).getDisplayManager();
    mDisplayManager.init(DisplayType.Car);
    mDisplayManager.addListener(DisplayType.Car, this);
    if (isStarted())
      takeDisplay();
  }

  private boolean isStarted()
  {
    return getLifecycle().getCurrentState().isAtLeast(Lifecycle.State.STARTED);
  }

  @Override
  protected void onStart()
  {
    super.onStart();
    MwmApplication.from(this).getLocationHelper().addListener(this);
    if (mDisplayManager != null)
      takeDisplay();
    updateNavBanner();
  }

  private void takeDisplay()
  {
    if (mDisplayManager.isCarDisplayUsed())
      showMap();
    else
      mDisplayManager.changeDisplay(DisplayType.Car);
  }

  @Override
  public void onDisplayChangedToCar(@NonNull Runnable onTaskFinishedCallback)
  {
    Logger.d(TAG);
    showMap();
    onTaskFinishedCallback.run();
  }

  @Override
  public void onDisplayChangedToDevice(@NonNull Runnable onTaskFinishedCallback)
  {
    Logger.d(TAG);
    mMapShown = false;
    mPlaceholder.setVisibility(View.VISIBLE);
    if (mMapController != null)
      Automotive.releaseMap(mMapController, onTaskFinishedCallback);
    else
      onTaskFinishedCallback.run();
  }

  private void showMap()
  {
    mPlaceholder.setVisibility(View.GONE);
    mMapView.setVisibility(View.VISIBLE);
    if (mMapController != null)
      mMapController.attachSurface();
    mMapShown = true;
    updateNavBanner();
    mFollowPending = true;
    followMyPosition();
  }

  // Like the maps card of other car launchers: the car's surroundings at street level, following its position.
  private void followMyPosition()
  {
    if (!Map.isEngineCreated())
      return;
    final Location location = MwmApplication.from(this).getLocationHelper().getSavedLocation();
    if (location == null)
      return; // retried from onLocationUpdated()
    mFollowPending = false;
    if (Framework.nativeGetDrawScale() < CARD_MIN_ZOOM)
      Framework.nativeZoomToPoint(location.getLatitude(), location.getLongitude(), CARD_ZOOM, true);
    final int mode = LocationState.getMode();
    if (mode == LocationState.NOT_FOLLOW || mode == LocationState.NOT_FOLLOW_NO_POSITION)
      LocationState.nativeSwitchToNextMode();
  }

  // While navigating (started in the full app), the card shows the next turn instead of the search bar.
  // Like MwmActivity's navigation panel, it's refreshed on every location update.
  private void updateNavBanner()
  {
    final RoutingInfo info =
        mMapShown && RoutingController.get().isNavigating() ? Framework.nativeGetRouteFollowingInfo() : null;
    UiUtils.showIf(info != null, mNavBanner);
    UiUtils.showIf(mMapShown && info == null, mSearchBar);
    if (info == null)
      return;

    if (Router.get() == Router.Pedestrian)
      mNavTurn.setImageResource(info.pedestrianDirection.getTurnRes());
    else
      mNavTurn.setImageResource(info.carDirection.getTurnRes(info.exitNum));
    mNavTurnDistance.setText(info.distToTurn.toString(this));
    mNavNextStreet.setText(info.nextStreet);
    mNavArrival.setText(Utils.formatArrivalTime(info.totalTimeInSeconds));
    mNavRemaining.setText(info.distToTarget.toString(this));
  }

  @Override
  public void onLocationUpdated(@NonNull Location location)
  {
    if (mFollowPending)
      followMyPosition();
    updateNavBanner();
  }

  @Override
  public void onRenderingCreated()
  {
    followMyPosition();
    // The process is already in the foreground, but location could not start before the engine existed.
    MwmApplication.from(this).getLocationHelper().resumeLocationInForeground();
  }

  private void openApp(@Nullable String booleanExtra)
  {
    // Before the first run is finished (no map shown yet), go through the regular start-up.
    final Intent intent = mDisplayManager == null ? getPackageManager().getLaunchIntentForPackage(getPackageName())
                                                  : new Intent(this, MwmActivity.class);
    if (intent == null)
      return;
    if (booleanExtra != null)
      intent.putExtra(booleanExtra, true);
    startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
  }

  @Override
  protected void onStop()
  {
    super.onStop();
    MwmApplication.from(this).getLocationHelper().removeListener(this);
  }

  @Override
  protected void onDestroy()
  {
    super.onDestroy();
    if (mDisplayManager != null && !isChangingConfigurations())
      mDisplayManager.removeListener(DisplayType.Car);
  }
}
