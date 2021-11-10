package ro.android.hype;

import android.app.PendingIntent;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.nfc.NfcAdapter;
import android.nfc.Tag;
import android.os.AsyncTask;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.view.Window;
import android.widget.ExpandableListAdapter;
import android.widget.ExpandableListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import org.json.JSONException;
import org.json.JSONObject;
import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;

import ro.android.hype.xmlutil.CategoriesParser;
import ro.android.hype.xmlutil.Category;
import ro.android.hype.xmlutil.Game;
import ro.android.hype.xmlutil.GamesParser;

public class MainActivity extends AppCompatActivity {

    private static final String URL_INFO = "https://bilet.kiddo.ro/rest.php?play/play_game/getinfo/card/%s/cksum/%s";
    private static final String URL_PLAY = "https://bilet.kiddo.ro/rest.php?play/play_game/payprice/game/%s/card/%s/cksum/%s";

    private NfcAdapter nfcAdapter;
    private PendingIntent pendingIntent;
    final static String TAG = "nfc_test";

    private ExpandableListView expandableListView;
    private ExpandableListAdapter expandableListAdapter;

    private ProgressDialog progressDialog;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getSupportActionBar().hide();

        //Initialise NfcAdapter
        nfcAdapter = NfcAdapter.getDefaultAdapter(this);

        //If no NfcAdapter, display that the device has no NFC
        if (nfcAdapter == null) {
            Toast.makeText(this, R.string.no_nfc, Toast.LENGTH_LONG).show();
            finish();
        }

        //Create a PendingIntent object so the Android system can
        //populate it with the details of the tag when it is scanned.
        //PendingIntent.getActivity(Context,requestcode(identifier for intent),intent,int)
        pendingIntent = PendingIntent.getActivity(this, 0, new Intent(this, this.getClass()).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP), 0);
        setContentView(R.layout.activity_main);

        getSupportFragmentManager().beginTransaction().replace(R.id.replaceable, ScanFragment.newInstance()).commitNow();
    }

    @Override
    protected void onStart() {
        super.onStart();

        if (!nfcAdapter.isEnabled()) {
            AlertDialog.Builder dlgAlert = new AlertDialog.Builder(this);
            dlgAlert.setMessage(R.string.nfc_not_active);
            dlgAlert.setCancelable(false);
            dlgAlert.setPositiveButton("Ok",
                    new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int which) {
                            startActivity(new Intent(Settings.ACTION_NFC_SETTINGS));
                        }
                    });
            dlgAlert.create().show();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        assert nfcAdapter != null;
        //nfcAdapter.enableForegroundDispatch(context,pendingIntent, intentFilterArray, techListsArray);
        nfcAdapter.enableForegroundDispatch(this, pendingIntent, null, null);
    }

    protected void onPause() {
        super.onPause();
        //On pause stop listening
        if (nfcAdapter != null) {
            nfcAdapter.disableForegroundDispatch(this);
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        resolveIntent(intent);
    }

    private void openInfoScreen(final String reversedDecimal, final String checksum) {
        try {
            MyAsyncTasks myAsyncTasks = new MyAsyncTasks();
            String result = myAsyncTasks.execute(String.format(URL_INFO, reversedDecimal, checksum)).get();

            if (result == null || result.trim().isEmpty()) {
                return;
            }

            JSONObject jsonObject = new JSONObject(result);

            String status = jsonObject.getString("status");

            if (!status.equalsIgnoreCase("OK")) {
                String message = jsonObject.getString("message");
                AlertDialog.Builder dlgAlert = new AlertDialog.Builder(this);
                dlgAlert.setMessage(message + getString(R.string.invalid_card));
                dlgAlert.setPositiveButton("Ok", null);
                dlgAlert.create().show();

                return;
            }

            getSupportFragmentManager().beginTransaction().replace(R.id.replaceable, ChooseGameFragment.newInstance()).commitNow();

            String name = jsonObject.getString("name");
            String balance = jsonObject.getString("balance");
            ((TextView) findViewById(R.id.textViewNameValue)).setText(name);
            ((TextView) findViewById(R.id.textViewCreditValue)).setText(balance);


            Map<Integer, List<Game>> gamesMap = new GamesParser().parse(getAssets().open("games.xml"));
            List<Category> categories = new CategoriesParser().parse(getAssets().open("categories.xml"));

            expandableListView = findViewById(R.id.expandableList);
            expandableListAdapter = new MyExpandableListAdapter(this, categories, gamesMap);
            expandableListView.setAdapter(expandableListAdapter);
            expandableListView.setOnGroupExpandListener(new ExpandableListView.OnGroupExpandListener() {
                int lastExppandedPosition = -1;

                @Override
                public void onGroupExpand(int groupPosition) {
                    if (lastExppandedPosition != -1 && groupPosition != lastExppandedPosition) {
                        expandableListView.collapseGroup(lastExppandedPosition);
                        lastExppandedPosition = groupPosition;
                    }
                }
            });
            expandableListView.setOnChildClickListener(new ExpandableListView.OnChildClickListener() {
                @Override
                public boolean onChildClick(ExpandableListView parent, View v, int groupPosition, int childPosition, long id) {
                    String gameID = ((Game) expandableListAdapter.getChild(groupPosition, childPosition)).getId();
                    gameSelected(gameID, reversedDecimal, checksum);
                    return true;
                }
            });

        } catch (IOException | XmlPullParserException e) {
            e.printStackTrace();
        } catch (ExecutionException | InterruptedException e) {
            e.printStackTrace();
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    private void gameSelected(String gameID, String reversedDecimal, String checksum) {
        MyAsyncTasks myAsyncTasks = new MyAsyncTasks();
        try {
            String result = myAsyncTasks.execute(String.format(URL_PLAY, gameID, reversedDecimal, checksum)).get();

            if (result == null || result.trim().isEmpty()) {
                return;
            }

            JSONObject jsonObject = new JSONObject(result);

            String status = jsonObject.getString("status");

            if (!status.equalsIgnoreCase("OK")) {
                String message = jsonObject.getString("message");

                AlertDialog.Builder dlgAlert = new AlertDialog.Builder(this);
                dlgAlert.setMessage(getString(R.string.error_pay) + message);
                dlgAlert.setCancelable(false);
                dlgAlert.setPositiveButton("Ok",
                        new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int which) {
                                getSupportFragmentManager().beginTransaction().replace(R.id.replaceable, ScanFragment.newInstance()).commitNow();
                            }
                        });
                dlgAlert.create().show();

                return;
            }

            String balance = jsonObject.getString("balance");

            AlertDialog.Builder dlgAlert = new AlertDialog.Builder(this);
            dlgAlert.setMessage(getString(R.string.success_pay) + balance);
            dlgAlert.setCancelable(false);
            dlgAlert.setPositiveButton("Ok",
                    new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int which) {
                            //return to first screen
                            getSupportFragmentManager().beginTransaction().replace(R.id.replaceable, ScanFragment.newInstance()).commitNow();
                        }
                    });
            dlgAlert.create().show();

        } catch (ExecutionException e) {
            e.printStackTrace();
        } catch (InterruptedException e) {
            e.printStackTrace();
        } catch (JSONException e) {
            e.printStackTrace();
        }

    }

    private void resolveIntent(Intent intent) {
        String action = intent.getAction();
        if (NfcAdapter.ACTION_TAG_DISCOVERED.equals(action)
                || NfcAdapter.ACTION_TECH_DISCOVERED.equals(action)
                || NfcAdapter.ACTION_NDEF_DISCOVERED.equals(action)) {
            Tag tag = intent.getParcelableExtra(NfcAdapter.EXTRA_TAG);
            assert tag != null;
//            byte[] payload = detectTagData(tag).getBytes();

//            System.out.println("reverse decimal=" + toReversedDec10Digits(tag.getId()));
//            System.out.println("getChecksum=" + getChecksum(toReversedDec10Digits(tag.getId())));

            openInfoScreen(toReversedDec10Digits(tag.getId()), getChecksum(toReversedDec10Digits(tag.getId())));
        }
    }

//    private String detectTagData(Tag tag) {
//        StringBuilder sb = new StringBuilder();
//        byte[] id = tag.getId();
//        sb.append("ID (hex): ").append(toHex(id)).append('\n');
//        sb.append("ID (reversed hex): ").append(toReversedHex(id)).append('\n');
//        sb.append("ID (dec): ").append(toDec(id)).append('\n');
//        sb.append("ID (reversed dec): ").append(toReversedDec(id)).append('\n');
//
//        String prefix = "android.nfc.tech.";
//        sb.append("Technologies: ");
//        for (String tech : tag.getTechList()) {
//            sb.append(tech.substring(prefix.length()));
//            sb.append(", ");
//        }
//
//        sb.delete(sb.length() - 2, sb.length());
//
//        for (String tech : tag.getTechList()) {
//            if (tech.equals(MifareClassic.class.getName())) {
//                sb.append('\n');
//                String type = "Unknown";
//
//                try {
//                    MifareClassic mifareTag = MifareClassic.get(tag);
//
//                    switch (mifareTag.getType()) {
//                        case MifareClassic.TYPE_CLASSIC:
//                            type = "Classic";
//                            break;
//                        case MifareClassic.TYPE_PLUS:
//                            type = "Plus";
//                            break;
//                        case MifareClassic.TYPE_PRO:
//                            type = "Pro";
//                            break;
//                    }
//                    sb.append("Mifare Classic type: ");
//                    sb.append(type);
//                    sb.append('\n');
//
//                    sb.append("Mifare size: ");
//                    sb.append(mifareTag.getSize() + " bytes");
//                    sb.append('\n');
//
//                    sb.append("Mifare sectors: ");
//                    sb.append(mifareTag.getSectorCount());
//                    sb.append('\n');
//
//                    sb.append("Mifare blocks: ");
//                    sb.append(mifareTag.getBlockCount());
//                } catch (Exception e) {
//                    sb.append("Mifare classic error: " + e.getMessage());
//                }
//            }
//
//            if (tech.equals(MifareUltralight.class.getName())) {
//                sb.append('\n');
//                MifareUltralight mifareUlTag = MifareUltralight.get(tag);
//                String type = "Unknown";
//                switch (mifareUlTag.getType()) {
//                    case MifareUltralight.TYPE_ULTRALIGHT:
//                        type = "Ultralight";
//                        break;
//                    case MifareUltralight.TYPE_ULTRALIGHT_C:
//                        type = "Ultralight C";
//                        break;
//                }
//                sb.append("Mifare Ultralight type: ");
//                sb.append(type);
//            }
//        }
//        Log.v("test", sb.toString());
//        return sb.toString();
//    }

//    private String toHex(byte[] bytes) {
//        StringBuilder sb = new StringBuilder();
//        for (int i = bytes.length - 1; i >= 0; --i) {
//            int b = bytes[i] & 0xff;
//            if (b < 0x10)
//                sb.append('0');
//            sb.append(Integer.toHexString(b));
//            if (i > 0) {
//                sb.append(" ");
//            }
//        }
//        return sb.toString();
//    }

//    private String toReversedHex(byte[] bytes) {
//        StringBuilder sb = new StringBuilder();
//        for (int i = 0; i < bytes.length; ++i) {
//            if (i > 0) {
//                sb.append(" ");
//            }
//            int b = bytes[i] & 0xff;
//            if (b < 0x10)
//                sb.append('0');
//            sb.append(Integer.toHexString(b));
//        }
//        return sb.toString();
//    }

//    private long toDec(byte[] bytes) {
//        long result = 0;
//        long factor = 1;
//        for (int i = 0; i < bytes.length; ++i) {
//            long value = bytes[i] & 0xffl;
//            result += value * factor;
//            factor *= 256l;
//        }
//        return result;
//    }

    private long toReversedDec(byte[] bytes) {
        long result = 0;
        long factor = 1;
        for (int i = bytes.length - 1; i >= 0; --i) {
            long value = bytes[i] & 0xffl;
            result += value * factor;
            factor *= 256l;
        }
        return result;
    }

    private String toReversedDec10Digits(byte[] bytes) {
        String reversedDecimal = String.valueOf(toReversedDec(bytes));
        while (reversedDecimal.length() < 10) {
            reversedDecimal = "0" + reversedDecimal;
        }
        return reversedDecimal;
    }

    private String getChecksum(String string) {
        int factor = 3;
        int odd_total = 0;
        int even_total = 0;

        for (int i = 0; i < string.length(); i++) {
            int j = string.charAt(i) - '0';

            if (((i + 1) % 2) == 0) {
                even_total += j;
            } else {
                odd_total += j;
            }
        }

        int sum = (factor * odd_total) + even_total;
        int check_digit = sum % 10;
        int result = (check_digit > 0) ? 10 - check_digit : check_digit;

        return String.valueOf(result);
    }

    public class MyAsyncTasks extends AsyncTask<String, String, String> {
        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            // display a progress dialog for good user experiance
            progressDialog = new ProgressDialog(MainActivity.this);
            progressDialog.setMessage(getString(R.string.processing_results));
            progressDialog.setCancelable(false);
            progressDialog.show();
        }

        @Override
        protected String doInBackground(String... params) {
            String result = "";
            try {
                URL url;
                HttpURLConnection urlConnection = null;
                try {
                    url = new URL(params[0]);
                    urlConnection = (HttpURLConnection) url.openConnection();

                    urlConnection.setRequestProperty("Accept", "*/*");
                    urlConnection.setRequestProperty("User-Agent", "PostmanRuntime/7.28.4");
                    urlConnection.setRequestProperty("Accept-Encoding", "gzip, deflate, br");
                    urlConnection.setRequestProperty("Connection", "keep-alive");
                    urlConnection.setRequestProperty("Authorization", "Basic d2ViYXBpOmE3MTA1YWZiY2QwNjhkOTQ=");
                    urlConnection.setRequestProperty("Content-Type", "application/json");
                    urlConnection.setDoInput(true);
                    urlConnection.setRequestMethod("GET");
//
                    System.out.println("Response code:" + urlConnection.getResponseCode());

                    if (urlConnection.getResponseCode() != 200) {
                        return null;
                    }

                    InputStream in = urlConnection.getInputStream();
                    InputStreamReader isw = new InputStreamReader(in);

                    int data = isw.read();
                    while (data != -1) {
                        result += (char) data;
                        data = isw.read();
                    }

                    // return the data to onPostExecute method
                    return result;
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    if (urlConnection != null) {
                        urlConnection.disconnect();
                    }
                }

            } catch (Exception e) {
                e.printStackTrace();
                return "Exception: " + e.getMessage();
            }
            return result;
        }


        @Override
        protected void onPostExecute(String jsonString) {
            progressDialog.dismiss();
            System.out.println("result=" + jsonString);

            if (jsonString == null || jsonString.trim().isEmpty()) {
                AlertDialog.Builder dlgAlert = new AlertDialog.Builder(MainActivity.this);
                dlgAlert.setMessage(getString(R.string.server_connection_problem));
                dlgAlert.setPositiveButton("Ok", null);
                dlgAlert.create().show();
            }

            super.onPostExecute(jsonString);
        }
    }

}
