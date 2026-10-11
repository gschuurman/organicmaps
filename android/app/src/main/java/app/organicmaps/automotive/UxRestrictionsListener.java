package app.organicmaps.automotive;

import android.car.Car;
import android.car.drivingstate.CarUxRestrictions;
import android.car.drivingstate.CarUxRestrictionsManager;
import android.content.Context;
import androidx.annotation.NonNull;
import androidx.core.util.Consumer;
import app.organicmaps.sdk.util.log.Logger;

// The only class that uses the car library, which exists on Android Automotive OS only.
final class UxRestrictionsListener
{
  private static final String TAG = UxRestrictionsListener.class.getSimpleName();

  private UxRestrictionsListener() {}

  // Listens for the whole life of the process.
  static void start(@NonNull Context context, @NonNull Consumer<DrivingRestrictions.State> onChanged)
  {
    final Car car = Car.createCar(context);
    final CarUxRestrictionsManager manager =
        car == null ? null : (CarUxRestrictionsManager) car.getCarManager(Car.CAR_UX_RESTRICTION_SERVICE);
    if (manager == null)
    {
      Logger.w(TAG, "No car UX restrictions service");
      return;
    }
    final CarUxRestrictionsManager.OnUxRestrictionsChangedListener listener =
        restrictions -> onChanged.accept(toState(restrictions));
    manager.registerListener(listener);
    listener.onUxRestrictionsChanged(manager.getCurrentCarUxRestrictions());
  }

  @NonNull
  private static DrivingRestrictions.State toState(@NonNull CarUxRestrictions restrictions)
  {
    final int active = restrictions.getActiveRestrictions();
    final boolean noKeyboard = (active & CarUxRestrictions.UX_RESTRICTIONS_NO_KEYBOARD) != 0;
    final int maxListItems = (active & CarUxRestrictions.UX_RESTRICTIONS_LIMIT_CONTENT) != 0
                               ? restrictions.getMaxCumulativeContentItems()
                               : Integer.MAX_VALUE;
    return new DrivingRestrictions.State(noKeyboard, maxListItems);
  }
}
