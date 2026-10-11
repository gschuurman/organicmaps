package app.organicmaps.automotive;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import app.organicmaps.sdk.bookmarks.data.MapObject;
import app.organicmaps.sdk.routing.RoutingController;
import java.util.ArrayList;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * Android Automotive OS, like Google Maps there: the home address and the latest places navigated to, listed under
 * the search on the map. Stored on the device only.
 */
public final class CarPlaces
{
  private static final String PREFS = "car_places";
  private static final String KEY_HOME = "home";
  private static final String KEY_RECENTS = "recents";
  private static final int MAX_RECENTS = 5;
  // About 10 m: the same place navigated to again.
  private static final double SAME_PLACE_DEGREES = 1e-4;

  public static final class Place
  {
    @NonNull
    public final String name;
    public final double lat;
    public final double lon;

    Place(@NonNull String name, double lat, double lon)
    {
      this.name = name;
      this.lat = lat;
      this.lon = lon;
    }

    @NonNull
    public MapObject toMapObject()
    {
      return MapObject.createMapObject(MapObject.API_POINT, name, "", lat, lon);
    }

    boolean isSameLocation(@NonNull Place other)
    {
      return Math.abs(lat - other.lat) < SAME_PLACE_DEGREES && Math.abs(lon - other.lon) < SAME_PLACE_DEGREES;
    }
  }

  // Bumped on every change, for the UI to refresh.
  private static final MutableLiveData<Integer> sChanges = new MutableLiveData<>(0);

  private CarPlaces() {}

  /** Starts remembering where navigation goes. */
  public static void init(@NonNull Context context)
  {
    if (!Automotive.isAutomotive(context))
      return;
    final Context appContext = context.getApplicationContext();
    RoutingController.get().addNavigationStateListener(active -> {
      if (active)
        addRecent(appContext, RoutingController.get().getEndPoint());
    });
  }

  @NonNull
  public static LiveData<Integer> getChanges()
  {
    return sChanges;
  }

  @Nullable
  public static Place getHome(@NonNull Context context)
  {
    final String json = prefs(context).getString(KEY_HOME, null);
    if (json == null)
      return null;
    try
    {
      return fromJson(new JSONObject(json));
    }
    catch (JSONException e)
    {
      throw new IllegalStateException("Invalid home " + json, e); // Only written by this class.
    }
  }

  public static void setHome(@NonNull Context context, @NonNull MapObject mapObject)
  {
    final Place home = new Place(nameOf(mapObject), mapObject.getLat(), mapObject.getLon());
    prefs(context).edit().putString(KEY_HOME, toJson(home).toString()).apply();
    // Home is listed on its own, not as a recent place too.
    final List<Place> recents = getRecents(context);
    if (recents.removeIf(home::isSameLocation))
      saveRecents(context, recents);
    notifyChanged();
  }

  /** Newest first. */
  @NonNull
  public static List<Place> getRecents(@NonNull Context context)
  {
    final String json = prefs(context).getString(KEY_RECENTS, "[]");
    final List<Place> recents = new ArrayList<>();
    try
    {
      final JSONArray array = new JSONArray(json);
      for (int i = 0; i < array.length(); i++)
        recents.add(fromJson(array.getJSONObject(i)));
    }
    catch (JSONException e)
    {
      throw new IllegalStateException("Invalid recent places " + json, e); // Only written by this class.
    }
    return recents;
  }

  private static void addRecent(@NonNull Context context, @Nullable MapObject destination)
  {
    if (destination == null || destination.isMyPosition())
      return;
    final Place place = new Place(nameOf(destination), destination.getLat(), destination.getLon());
    final Place home = getHome(context);
    if (home != null && home.isSameLocation(place))
      return;
    final List<Place> recents = getRecents(context);
    recents.removeIf(place::isSameLocation);
    recents.add(0, place);
    while (recents.size() > MAX_RECENTS)
      recents.remove(recents.size() - 1);
    saveRecents(context, recents);
    notifyChanged();
  }

  private static void saveRecents(@NonNull Context context, @NonNull List<Place> recents)
  {
    final JSONArray array = new JSONArray();
    for (Place place : recents)
      array.put(toJson(place));
    prefs(context).edit().putString(KEY_RECENTS, array.toString()).apply();
  }

  // Unnamed places (e.g. a point chosen on the map) are shown with their address.
  @NonNull
  private static String nameOf(@NonNull MapObject mapObject)
  {
    return TextUtils.isEmpty(mapObject.getTitle()) ? mapObject.getSubtitle() : mapObject.getTitle();
  }

  private static void notifyChanged()
  {
    final Integer changes = sChanges.getValue();
    sChanges.setValue(changes == null ? 1 : changes + 1);
  }

  @NonNull
  private static JSONObject toJson(@NonNull Place place)
  {
    try
    {
      return new JSONObject().put("name", place.name).put("lat", place.lat).put("lon", place.lon);
    }
    catch (JSONException e)
    {
      throw new AssertionError(e); // Only thrown for non-finite numbers, coordinates are finite.
    }
  }

  @NonNull
  private static Place fromJson(@NonNull JSONObject json) throws JSONException
  {
    return new Place(json.getString("name"), json.getDouble("lat"), json.getDouble("lon"));
  }

  @NonNull
  private static SharedPreferences prefs(@NonNull Context context)
  {
    return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
  }
}
