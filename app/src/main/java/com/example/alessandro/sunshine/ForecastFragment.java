package com.example.alessandro.sunshine;

import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v4.app.Fragment;
import android.text.format.Time;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;

 /*http://api.openweathermap.org/data/2.5/forecast/daily?q=Milan&units=metric&cnt=7&mode=json*/

/**
 * A placeholder fragment containing a simple view.
 */
public class ForecastFragment extends Fragment {
	private ArrayAdapter<String> mForecastAdapter;

	public ForecastFragment() {
	}

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setHasOptionsMenu(true);
	}

	@Override
	public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
		inflater.inflate(R.menu.forecastfragment, menu);
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		int id = item.getItemId();

		if (id == R.id.action_refresh) {
			updateWeather();
			return true;
		}

		return super.onOptionsItemSelected(item);
	}

	private void updateWeather() {
		FetchWeatherTask fetchWeatherTask = new FetchWeatherTask();
		SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(getActivity());
		String loc = pref
				.getString(getString(R.string.location_key), getString(R.string.default_location));
		fetchWeatherTask.execute(loc);
	}

	public void onStart() {
		super.onStart();
		updateWeather();
	}

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container,
			Bundle savedInstanceState) {
		View rootView = inflater.inflate(R.layout.fragment_main, container, false);

		List<String> weekForecast = new ArrayList<>();
/*        for (int i = 0; i < 7; i++)
        {
            weekForecast.add("Today - Sunny - 10/25");
        }
*/
		mForecastAdapter = new ArrayAdapter<String>(getActivity(), R.layout.list_item_forecast,
				R.id.list_item_forecast_textview, weekForecast);

		final ListView listView = (ListView) rootView.findViewById(R.id.listview_forecast);
		listView.setAdapter(mForecastAdapter);

		listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
			@Override
			public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
				String forecast = mForecastAdapter.getItem(position);

				Toast toast = Toast.makeText(getActivity(), forecast, Toast.LENGTH_SHORT);
				toast.show();

				Intent intent = new Intent(getActivity(), DetailActivity.class)
						.putExtra(Intent.EXTRA_TEXT, forecast);
				startActivity(intent);
			}
		});


		return rootView;
	}

	public class FetchWeatherTask extends AsyncTask<String, Void, String[]> {
		private final String LOG_TAG = FetchWeatherTask.class.getSimpleName();

		/* The date/time conversion code is going to be moved outside the asynctask later,
		 * so for convenience we're breaking it out into its own method now.
		 */
		private String getReadableDateString(long time) {
			// Because the API returns a unix timestamp (measured in seconds),
			// it must be converted to milliseconds in order to be converted to valid date.
			SimpleDateFormat shortenedDateFormat = new SimpleDateFormat("EEE dd MMM");
			return shortenedDateFormat.format(time);
		}

		/**
		 * Prepare the weather high/lows for presentation.
		 */
		private String formatHighLows(double low, double high) {
			//If imperial unit selected, transform values in F
			SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(getActivity());
			String unitType = pref.getString(getString(R.string.unit_key), "metric");
			if (unitType.equals("imperial")) {
				high = (high * 180 / 100) + 32;
				low = (low * 180 / 100) + 32;
			}
			// For presentation, assume the user doesn't care about tenths of a degree.
			long roundedLow = Math.round(low);
			long roundedHigh = Math.round(high);

			String highLowStr = roundedLow + "/" + roundedHigh;
			return highLowStr;
		}

		/**
		 * Take the String representing the complete forecast in JSON Format and
		 * pull out the data we need to construct the Strings needed for the wireframes.
		 * <p>
		 * Fortunately parsing is easy:  constructor takes the JSON string and converts it
		 * into an Object hierarchy for us.
		 */
		private String[] getWeatherDataFromJson(String forecastJsonStr,
				int numDays) throws JSONException {

			// These are the names of the JSON objects that need to be extracted.
			final String OWM_LIST = "list";
			final String OWM_WEATHER = "weather";
			final String OWM_TEMPERATURE = "temp";
			final String OWM_MAX = "max";
			final String OWM_MIN = "min";
			final String OWM_DESCRIPTION = "main";

			JSONObject forecastJson = new JSONObject(forecastJsonStr);
			JSONArray weatherArray = forecastJson.getJSONArray(OWM_LIST);

			// OWM returns daily forecasts based upon the local time of the city that is being
			// asked for, which means that we need to know the GMT offset to translate this data
			// properly.

			// Since this data is also sent in-order and the first day is always the
			// current day, we're going to take advantage of that to get a nice
			// normalized UTC date for all of our weather.

			Time dayTime = new Time();
			dayTime.setToNow();

			// we start at the day returned by local time. Otherwise this is a mess.
			int julianStartDay = Time.getJulianDay(System.currentTimeMillis(), dayTime.gmtoff);

			// now we work exclusively in UTC
			dayTime = new Time();

			String[] resultStrs = new String[numDays];
			for (int i = 0; i < weatherArray.length(); i++) {
				// For now, using the format "Day, description, hi/low"
				String day;
				String description;
				String lowAndHigh;

				// Get the JSON object representing the day
				JSONObject dayForecast = weatherArray.getJSONObject(i);

				// The date/time is returned as a long.  We need to convert that
				// into something human-readable, since most people won't read "1400356800" as
				// "this saturday".
				long dateTime;
				// Cheating to convert this to UTC time, which is what we want anyhow
				dateTime = dayTime.setJulianDay(julianStartDay + i);
				day = getReadableDateString(dateTime);

				// description is in a child array called "weather", which is 1 element long.
				JSONObject weatherObject = dayForecast.getJSONArray(OWM_WEATHER).getJSONObject(0);
				description = weatherObject.getString(OWM_DESCRIPTION);

				// Temperatures are in a child object called "temp".  Try not to name variables
				// "temp" when working with temperature.  It confuses everybody.
				JSONObject temperatureObject = dayForecast.getJSONObject(OWM_TEMPERATURE);

				double high = temperatureObject.getDouble(OWM_MAX);
				double low = temperatureObject.getDouble(OWM_MIN);

				lowAndHigh = formatHighLows(low, high);
				resultStrs[i] = day + " - " + description + " - " + lowAndHigh;
			}


			return resultStrs;

		}

		@Override
		protected String[] doInBackground(String... params) {
			if (params.length == 0) {
				return null;
			}
			// These two need to be declared outside the try/catch
			// so that they can be closed in the finally block.
			HttpURLConnection urlConnection = null;
			BufferedReader reader = null;

			// Will contain the raw JSON response as a string.
			String forecastJsonStr = null;
			URL url = null;

			//Build the openweathermaps url with
			Integer dayParam = 7;
			try {
				final String BASE_URL = "http://api.openweathermap.org/data/2.5/forecast/daily?";
				final String CITY_PARAM = "q";
				final String DAYS_PARAM = "cnt";
				final String UNITS_PARAM = "units";
				final String TYPE = "mode";
				final String APPID = "APPID";

				Uri urlBuild = Uri.parse(BASE_URL).buildUpon()
						.appendQueryParameter(CITY_PARAM, params[0])
						.appendQueryParameter(UNITS_PARAM, "metric")
						.appendQueryParameter(DAYS_PARAM, dayParam.toString())
						.appendQueryParameter(TYPE, "json")
						.appendQueryParameter(APPID, "62c14e12a82697d41ce25273b632f7db").build();

				try {
					url = new URL(urlBuild.toString());
				} catch (MalformedURLException e) {
					Log.e(LOG_TAG, "Bad Built");
				}

			} catch (Exception e) {
				Log.e(LOG_TAG, "Error");
			}

			try {
				// Construct the URL for the OpenWeatherMap query
				// Possible parameters are avaiable at OWM's forecast API page, at
				// http://openweathermap.org/API#forecast


				// Create the request to OpenWeatherMap, and open the connection
				urlConnection = (HttpURLConnection) url.openConnection();
				urlConnection.setRequestMethod("GET");
				urlConnection.connect();

				// Read the input stream into a String
				InputStream inputStream = urlConnection.getInputStream();
				StringBuffer buffer = new StringBuffer();
				if (inputStream == null) {
					// Nothing to do.
					return null;
				}
				reader = new BufferedReader(new InputStreamReader(inputStream));

				String line;
				try {
					while ((line = reader.readLine()) != null) {
						// Since it's JSON, adding a newline isn't necessary (it won't affect parsing)
						// But it does make debugging a *lot* easier if you print out the completed
						// buffer for debugging.
						buffer.append(line + "\n");
					}
				} catch (IOException e) {
					Log.e(LOG_TAG, "Error ", e);
				}

				if (buffer.length() == 0) {
					// Stream was empty.  No point in parsing.
					return null;
				}
				forecastJsonStr = buffer.toString();

				Log.v(LOG_TAG, "Forecast JSon string = " + forecastJsonStr);

			} catch (IOException e) {
				Log.e(LOG_TAG, "Error ", e);
				// If the code didn't successfully get the weather data, there's no point in attemping
				// to parse it.
				return null;
			} finally {
				if (urlConnection != null) {
					urlConnection.disconnect();
				}
				if (reader != null) {
					try {
						reader.close();
					} catch (final IOException e) {
						Log.e(LOG_TAG, "Error closing stream", e);
					}
				}
			}
			try {
				return getWeatherDataFromJson(forecastJsonStr, dayParam);
			} catch (JSONException e) {
				Log.e(LOG_TAG, e.getMessage(), e);
				e.printStackTrace();
			}
			//return null if there is an error
			return null;
		}

		@Override
		protected void onPostExecute(String[] strings) {
			if (strings != null) {
				mForecastAdapter.clear();
				for (String dayString : strings) {
					mForecastAdapter.add(dayString);
				}
			}
		}

	}

}
