package app.organicmaps.automotive;

import android.content.Context;
import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

/**
 * What can't be used while driving on Android Automotive OS (the car's UX restrictions), like Google Maps there:
 * the keyboard (voice search still can) and long lists. Never restricted on other devices.
 */
public final class DrivingRestrictions
{
  public static final class State
  {
    public final boolean keyboardRestricted;
    // Integer.MAX_VALUE when lists are not limited.
    public final int maxListItems;

    State(boolean keyboardRestricted, int maxListItems)
    {
      this.keyboardRestricted = keyboardRestricted;
      this.maxListItems = maxListItems;
    }
  }

  private static final State UNRESTRICTED = new State(false, Integer.MAX_VALUE);

  private static final MutableLiveData<State> sState = new MutableLiveData<>(UNRESTRICTED);

  private DrivingRestrictions() {}

  public static void init(@NonNull Context context)
  {
    if (Automotive.isAutomotive(context))
      UxRestrictionsListener.start(context.getApplicationContext(), sState::postValue);
  }

  @NonNull
  public static LiveData<State> get()
  {
    return sState;
  }
}
