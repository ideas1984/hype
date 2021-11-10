package ro.android.hype;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import android.app.PendingIntent;
import android.content.DialogInterface;
import android.content.Intent;
import android.nfc.NfcAdapter;
import android.nfc.Tag;
import android.nfc.tech.MifareClassic;
import android.nfc.tech.MifareUltralight;
import android.os.Bundle;
import android.provider.Settings;
import android.util.Log;
import android.view.View;
import android.view.Window;
import android.widget.ExpandableListAdapter;
import android.widget.ExpandableListView;
import android.widget.Toast;

import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import ro.android.hype.xmlutil.CategoriesParser;
import ro.android.hype.xmlutil.Category;
import ro.android.hype.xmlutil.Game;
import ro.android.hype.xmlutil.GamesParser;

public class MainActivity extends AppCompatActivity {

    private NfcAdapter nfcAdapter;
    private PendingIntent pendingIntent;
    final static String TAG = "nfc_test";

    private ExpandableListView expandableListView;
    private ExpandableListAdapter expandableListAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getSupportActionBar().hide();


        //Initialise NfcAdapter
        nfcAdapter = NfcAdapter.getDefaultAdapter(this);

        //If no NfcAdapter, display that the device has no NFC
        if (nfcAdapter == null) {
            Toast.makeText(this, "Acest telefon nu dispune de functia NFC", Toast.LENGTH_LONG).show();
            finish();
        }

        //Create a PendingIntent object so the Android system can
        //populate it with the details of the tag when it is scanned.
        //PendingIntent.getActivity(Context,requestcode(identifier for intent),intent,int)
        pendingIntent = PendingIntent.getActivity(this, 0, new Intent(this, this.getClass()).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP), 0);
        setContentView(R.layout.activity_main);

        ScanFragment fragment = ScanFragment.newInstance();
        getSupportFragmentManager().beginTransaction().replace(R.id.replaceable, fragment).commitNow();

    }

    @Override
    protected void onStart() {
        super.onStart();

        if (!nfcAdapter.isEnabled()) {
            AlertDialog.Builder dlgAlert = new AlertDialog.Builder(this);
            dlgAlert.setMessage("Functia NFC este dezactivata. Vei fi redirectionat in setarile telefonului.\nActiveaza NFC-ul si intoarce-te inapoi in aplicatie!");
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
        //Onpause stop listening
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

    public static String getDec(String hex) {
        String newHex = hex.substring(6, 8) + hex.substring(4, 6) + hex.substring(2, 4) + hex.substring(0, 2);
        System.out.println(newHex);

        String dec = String.valueOf(Long.parseLong(newHex, 16));

        while (dec.length() < 10) {
            dec = "0" + dec;
        }
        return dec;
    }

    public static long getDecimal(String hex) {
        String digits = "0123456789ABCDEF";
        hex = hex.toUpperCase();
        long val = 0;
        for (int i = 0; i < hex.length(); i++) {
            char c = hex.charAt(i);
            int d = digits.indexOf(c);
            val = 16 * val + d;
        }
        return val;
    }

    private String getMariusReversedDec(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (int i = bytes.length - 1; i >= 0; --i) {
            int b = bytes[i] & 0xff;
            if (b < 0x10)
                sb.append('0');
            sb.append(Integer.toHexString(b));
        }

        return getDec(sb.toString());


    }

    private void openInfoScreen() {
        ChooseGameFragment fragment = ChooseGameFragment.newInstance();
        getSupportFragmentManager().beginTransaction().replace(R.id.replaceable, fragment).commitNow();

        try {
            Map<Integer, List<Game>> gamesMap = new GamesParser().parse(getAssets().open("games.xml"));

//            for(List<Game> gamesList : gamesMap.values()) {
//                for(Game game : gamesList) {
//                    System.out.println(game.getId() + "    " + game.getName() + "   " +  game.getCategory());
//                }
//            }

            List<Category> categories = new CategoriesParser().parse(getAssets().open("categories.xml"));

//            for(Category category : categories) {
//                System.out.println(category.getId() + "    " + category.getName());
//            }


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
                    String selected = expandableListAdapter.getChild(groupPosition, childPosition).toString();
                    Toast.makeText(getApplicationContext(), "Selected: " + selected, Toast.LENGTH_SHORT).show();
                    return true;
                }
            });

        } catch (IOException | XmlPullParserException e) {
            e.printStackTrace();
        }
    }

    private void resolveIntent(Intent intent) {
        String action = intent.getAction();
        if (NfcAdapter.ACTION_TAG_DISCOVERED.equals(action)
                || NfcAdapter.ACTION_TECH_DISCOVERED.equals(action)
                || NfcAdapter.ACTION_NDEF_DISCOVERED.equals(action)) {
            Tag tag = (Tag) intent.getParcelableExtra(NfcAdapter.EXTRA_TAG);
            assert tag != null;
            byte[] payload = detectTagData(tag).getBytes();

            System.out.println("reverse decimal=" + toReversedDec10Digits(tag.getId()));
            System.out.println("getChecksum=" + getChecksum(toReversedDec10Digits(tag.getId())));

            openInfoScreen();
        }
    }

    private String detectTagData(Tag tag) {
        StringBuilder sb = new StringBuilder();
        byte[] id = tag.getId();
        sb.append("ID (hex): ").append(toHex(id)).append('\n');
        sb.append("ID (reversed hex): ").append(toReversedHex(id)).append('\n');
        sb.append("ID (dec): ").append(toDec(id)).append('\n');
        sb.append("ID (reversed dec): ").append(toReversedDec(id)).append('\n');

        String prefix = "android.nfc.tech.";
        sb.append("Technologies: ");
        for (String tech : tag.getTechList()) {
            sb.append(tech.substring(prefix.length()));
            sb.append(", ");
        }

        sb.delete(sb.length() - 2, sb.length());

        for (String tech : tag.getTechList()) {
            if (tech.equals(MifareClassic.class.getName())) {
                sb.append('\n');
                String type = "Unknown";

                try {
                    MifareClassic mifareTag = MifareClassic.get(tag);

                    switch (mifareTag.getType()) {
                        case MifareClassic.TYPE_CLASSIC:
                            type = "Classic";
                            break;
                        case MifareClassic.TYPE_PLUS:
                            type = "Plus";
                            break;
                        case MifareClassic.TYPE_PRO:
                            type = "Pro";
                            break;
                    }
                    sb.append("Mifare Classic type: ");
                    sb.append(type);
                    sb.append('\n');

                    sb.append("Mifare size: ");
                    sb.append(mifareTag.getSize() + " bytes");
                    sb.append('\n');

                    sb.append("Mifare sectors: ");
                    sb.append(mifareTag.getSectorCount());
                    sb.append('\n');

                    sb.append("Mifare blocks: ");
                    sb.append(mifareTag.getBlockCount());
                } catch (Exception e) {
                    sb.append("Mifare classic error: " + e.getMessage());
                }
            }

            if (tech.equals(MifareUltralight.class.getName())) {
                sb.append('\n');
                MifareUltralight mifareUlTag = MifareUltralight.get(tag);
                String type = "Unknown";
                switch (mifareUlTag.getType()) {
                    case MifareUltralight.TYPE_ULTRALIGHT:
                        type = "Ultralight";
                        break;
                    case MifareUltralight.TYPE_ULTRALIGHT_C:
                        type = "Ultralight C";
                        break;
                }
                sb.append("Mifare Ultralight type: ");
                sb.append(type);
            }
        }
        Log.v("test", sb.toString());
        return sb.toString();
    }

    private String toHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (int i = bytes.length - 1; i >= 0; --i) {
            int b = bytes[i] & 0xff;
            if (b < 0x10)
                sb.append('0');
            sb.append(Integer.toHexString(b));
            if (i > 0) {
                sb.append(" ");
            }
        }
        return sb.toString();
    }

    private String toReversedHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < bytes.length; ++i) {
            if (i > 0) {
                sb.append(" ");
            }
            int b = bytes[i] & 0xff;
            if (b < 0x10)
                sb.append('0');
            sb.append(Integer.toHexString(b));
        }
        return sb.toString();
    }

    private long toDec(byte[] bytes) {
        long result = 0;
        long factor = 1;
        for (int i = 0; i < bytes.length; ++i) {
            long value = bytes[i] & 0xffl;
            result += value * factor;
            factor *= 256l;
        }
        return result;
    }

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

}
