package app.organicmaps.sdk;

import android.view.SurfaceHolder;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.res.ConfigurationHelper;
import androidx.lifecycle.DefaultLifecycleObserver;
import androidx.lifecycle.LifecycleOwner;
import app.organicmaps.sdk.location.LocationHelper;
import app.organicmaps.sdk.util.log.Logger;

public class MapController implements DefaultLifecycleObserver
{
  private static final String TAG_PREFIX = MapController.class.getSimpleName();
  @NonNull
  private final String TAG;

  @NonNull
  private final MapView mMapView;
  @NonNull
  private final Map mMap;

  @Nullable
  private Runnable mOnSurfaceDestroyedListener = null;

  public MapController(@NonNull MapView mapView, @NonNull LocationHelper locationHelper,
                       @NonNull MapRenderingListener mapRenderingListener,
                       @Nullable Map.CallbackUnsupported callbackUnsupported, boolean launchByDeepLink)
  {
    mMapView = mapView;
    mMap = mMapView.getMap();
    mMap.onCreate(launchByDeepLink);
    mMap.setLocationHelper(locationHelper);
    mMap.setMapRenderingListener(mapRenderingListener);
    mMap.setCallbackUnsupported(callbackUnsupported);
    TAG = TAG_PREFIX + "[" + mMap.getDisplayType() + "]";
  }

  public MapView getView()
  {
    return mMapView;
  }

  public boolean isRenderingActive()
  {
    return Map.isEngineCreated() && mMap.isContextCreated();
  }

  public void updateCompassOffset(int offsetX, int offsetY)
  {
    mMap.updateCompassOffset(mMapView.getContext(), offsetX, offsetY, true);
  }

  public void updateBottomWidgetsOffset(int offsetX, int offsetY)
  {
    mMap.updateBottomWidgetsOffset(mMapView.getContext(), offsetX, offsetY);
  }

  public void updateMyPositionRoutingOffset(int offsetY)
  {
    mMap.updateMyPositionRoutingOffset(offsetY);
  }

  /**
   * Detaches the map from the rendering engine right away, without waiting for the view's surface to be destroyed,
   * so that another display can attach its own surface next. No-op if it is not attached.
   */
  public void detachSurface()
  {
    mMap.onSurfaceDestroyed(false);
  }

  /**
   * Attaches the view's current surface to the engine if it exists but is not attached, e.g. after
   * {@link #detachSurface()} when the view was shown again without its surface being recreated.
   */
  public void attachSurface()
  {
    final SurfaceHolder holder = mMapView.getHolder();
    if (mMap.isSurfaceAttached() || !holder.getSurface().isValid())
      return;
    mMap.onSurfaceCreated(mMapView.getContext(), holder.getSurface(), holder.getSurfaceFrame(),
                          ConfigurationHelper.getDensityDpi(mMapView.getResources()));
  }

  public void setOnDestroyListener(@NonNull Runnable task)
  {
    mOnSurfaceDestroyedListener = task;
  }

  @Override
  public void onStart(@NonNull LifecycleOwner owner)
  {
    Logger.d(TAG);
    mMap.onStart();
  }

  @Override
  public void onResume(@NonNull LifecycleOwner owner)
  {
    Logger.d(TAG);
    mMap.onResume();
  }

  @Override
  public void onPause(@NonNull LifecycleOwner owner)
  {
    Logger.d(TAG);
    mMap.onPause();
  }

  @Override
  public void onStop(@NonNull LifecycleOwner owner)
  {
    Logger.d(TAG);
    mMap.onStop();
  }

  @Override
  public void onDestroy(@NonNull LifecycleOwner owner)
  {
    Logger.d(TAG);
    mMap.setMapRenderingListener(null);
    mMap.setCallbackUnsupported(null);
    if (mOnSurfaceDestroyedListener != null)
    {
      mOnSurfaceDestroyedListener.run();
      mOnSurfaceDestroyedListener = null;
    }
  }
}
