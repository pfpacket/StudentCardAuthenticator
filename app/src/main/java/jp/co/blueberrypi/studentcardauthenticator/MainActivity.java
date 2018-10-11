package jp.co.blueberrypi.studentcardauthenticator;

import android.app.PendingIntent;
import android.content.Intent;
import android.net.http.SslError;
import android.nfc.NfcAdapter;
import android.nfc.Tag;
import android.nfc.tech.NfcF;
import android.os.Bundle;
import android.os.Vibrator;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.View;
import android.view.Menu;
import android.view.MenuItem;
import android.view.WindowManager;
import android.webkit.CookieManager;
import android.webkit.SslErrorHandler;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

import static android.nfc.NfcAdapter.FLAG_READER_NFC_F;
import static android.nfc.NfcAdapter.FLAG_READER_SKIP_NDEF_CHECK;

public class MainActivity extends AppCompatActivity implements NfcAdapter.ReaderCallback {

    private PendingIntent pendingIntent;
    private NfcAdapter nfcAdapter;
    private Tag tag;
    private static byte NFC_F_CMD_RWOE = 0x06;
    private static String SHIZ_UNIV_ID_SYS_CODE = "048B";
    //private static String SHIZ_UNIV_ID_MFG_CODE = "0120220427674EFF";
    Vibrator vibrator;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        vibrator = (Vibrator) getSystemService(VIBRATOR_SERVICE);

        FloatingActionButton fab = (FloatingActionButton) findViewById(R.id.fab);
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Snackbar.make(view, "WIP as U expected :(", Snackbar.LENGTH_LONG)
                        .setAction("Action", null).show();
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();

        nfcAdapter = NfcAdapter.getDefaultAdapter(this);
        if (nfcAdapter == null) {
            Toast.makeText(MainActivity.this, "NFC-F/FeliCaが有効ではありません", Toast.LENGTH_SHORT).show();
            super.finish();
        }

        nfcAdapter.enableReaderMode(this, this, FLAG_READER_NFC_F | FLAG_READER_SKIP_NDEF_CHECK, null);
    }

    private void vibrate(long[] pattern) {
        if (vibrator != null && vibrator.hasVibrator()) {
            vibrator.vibrate(pattern, -1);
        }
    }

    @Override
    public void onTagDiscovered(Tag tag) {
        MainActivity.this.tag = tag;

        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                NfcF nfcf = NfcF.get(MainActivity.this.tag);

                byte[] rwoe_res = null, rwoe = readWithoutEncryption(MainActivity.this.tag.getId(), new byte[]{(byte) 0x20, (byte) 0x0B}, 2);
                try {
                    if (rwoe == null) {
                        Toast.makeText(MainActivity.this, "Fatal: ByteArrayOutputStream.write() failed", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    nfcf.connect();
                    rwoe_res = nfcf.transceive(rwoe);

                    if (rwoe_res == null) {
                        Toast.makeText(MainActivity.this, "NFC-F/FeliCa通信異常", Toast.LENGTH_SHORT).show();
                        vibrate(new long[]{0, 800});
                        return;
                    }
                } catch (IOException e) {
                    Log.e("SCAUTH", "NFC-F/FeliCa: " + e.toString());
                    Toast.makeText(MainActivity.this,"NFC-F/FeliCa通信失敗\n" + e.toString(), Toast.LENGTH_SHORT).show();
                    vibrate(new long[]{0, 800});
                    return;
                }

                StudentCardInfo info = new StudentCardInfo();

                info.shizudaiID = parseRwoeString(rwoe_res, 37, 8);
                info.studentNumber = parseRwoeString(rwoe_res, 14, 8);
                info.IDm = bytesToString(MainActivity.this.tag.getId());
                info.PMm = bytesToString(nfcf.getManufacturer());
                info.systemCode = bytesToString(nfcf.getSystemCode());

                boolean isStudentCard = info.systemCode.equals(SHIZ_UNIV_ID_SYS_CODE)
                        && (info.shizudaiID != null || info.studentNumber != null);

                if (isStudentCard) {
                    Toast.makeText(MainActivity.this,
                            String.format("静岡大学学生証です\nIDm: %s\nSystem Code: %s\nPMm: %s\n学籍番号: %s\n静大ID: %s",
                                    info.IDm, SHIZ_UNIV_ID_SYS_CODE, info.PMm, info.studentNumber, info.shizudaiID),
                            Toast.LENGTH_LONG).show();
                    vibrate(new long[]{0, 80});

                    /* Start a webview to display the BLUEBERRY PI control page */
                    Intent webViewIntent = new Intent(MainActivity.this, WebViewActivity.class);
                    webViewIntent.putExtra(WebViewActivity.EXTRA_STUDENT_CARD_INFO, info);
                    startActivity(webViewIntent);
                } else {
                    Toast.makeText(MainActivity.this, "静岡大学学生証ではありません", Toast.LENGTH_SHORT).show();
                    vibrate(new long[]{0, 800});
                }
            }
        });
    }

    private static String parseRwoeString(byte[] res, int offset, int length) {
        if (res[10] != 0)
            return null;
        //int buflen = res[12];
        return new String(res, offset, length);
    }

    private byte[] readWithoutEncryption(byte[] idm, byte[] serviceCode, int blockNum)  {
        ByteArrayOutputStream bout = new ByteArrayOutputStream(128);

        bout.write(0);              // data length
        bout.write(NFC_F_CMD_RWOE);    // command code
        try {
            bout.write(idm);           // IDm
        } catch (IOException e) {
            return null;
        }
        bout.write(1);              // service length
        bout.write(serviceCode[1]);    // service code
        bout.write(serviceCode[0]);
        bout.write(blockNum);          // block size

        // block element
        for (int block = 0; block < blockNum; ++block) {
            bout.write(0x80);       //
            bout.write(block);         // block number
        }

        byte[] rwoe_request = bout.toByteArray();
        rwoe_request[0] = (byte) rwoe_request.length;

        return rwoe_request;
    }

    public String bytesToString(byte[] bytes) {
        StringBuffer sb = new StringBuffer();
        for (byte bt : bytes) {
            sb.append(String.format("%02X", bt));
        }
        return sb.toString();
    }

    @Override
    protected void onPause() {
        super.onPause();

        if (nfcAdapter != null)
            nfcAdapter.disableReaderMode(this);
    }

    private void showNfcInfo(byte[] idm, byte[] pmm, byte[] systemCode) {
        StringBuffer sb = new StringBuffer();
        sb.append("IDm: ");
        for(int i = 0; i < idm.length; i++) {
            sb.append(String.format("%02X",idm[i]));
        }
        sb.append("\n");
        sb.append("PMm: ");
        for(int i = 0; i < pmm.length; i++) {
            sb.append(String.format("%02X",pmm[i]));
        }
        sb.append("\n");
        sb.append("System Code: ");
        for(int i = 0; i < systemCode.length; i++) {
            sb.append(String.format("%02X",systemCode[i]));
        }
        sb.append("\n");

        Toast.makeText(MainActivity.this, sb.toString(), Toast.LENGTH_SHORT).show();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }
}
