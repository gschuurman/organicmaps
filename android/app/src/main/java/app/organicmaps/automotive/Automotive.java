package app.organicmaps.automotive;

import android.content.Context;
import android.content.pm.PackageManager;
import android.view.View;
import androidx.annotation.NonNull;
import app.organicmaps.sdk.MapController;

public final class Automotive
{
  private Automotive() {}

  // Android Automotive OS: the app runs on the head unit itself (not projected like Android Auto).
  public static boolean isAutomotive(@NonNull Context context)
  {
    return context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_AUTOMOTIVE);
  }

  /**
   * Hands the map over to the other display (map card or full app): detaches it from the engine right away and hides
   * it. Waiting for the view's own surfaceDestroyed() is not enough, it never comes when the window is already
   * hidden (e.g. the launcher was just covered by the full app).
   */
  public static void releaseMap(@NonNull MapController mapController, @NonNull Runnable onReleased)
  {
    mapController.detachSurface();
    mapController.getView().setVisibility(View.GONE);
    onReleased.run();
  }
}
