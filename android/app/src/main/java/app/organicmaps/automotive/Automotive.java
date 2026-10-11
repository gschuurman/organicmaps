package app.organicmaps.automotive;

import android.content.Context;
import android.content.pm.PackageManager;
import androidx.annotation.NonNull;

public final class Automotive
{
  private Automotive() {}

  // Android Automotive OS: the app runs on the head unit itself (not projected like Android Auto).
  public static boolean isAutomotive(@NonNull Context context)
  {
    return context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_AUTOMOTIVE);
  }
}
