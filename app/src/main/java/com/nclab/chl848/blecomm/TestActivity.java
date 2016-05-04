package com.nclab.chl848.blecomm;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.ImageButton;
import android.widget.TextView;

public class TestActivity extends Activity {

    TestView m_view;
    private int m_interval;
    private ImageButton m_up;
    private ImageButton m_down;
    private TextView m_lbInterval;
    private TextView m_lbIntervalTile;
    private Button m_pingButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_test);

        m_view = (TestView)findViewById(R.id.test_view);

        String isHost = BLEHandler.getInstance().isCentral() ? "Yes" : "No";
        ((TextView)findViewById(R.id.lbIsHost)).setText(isHost);

        m_pingButton = (Button)findViewById(R.id.btnPing);
        m_pingButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (m_view.isPing()) {
                    m_view.stopPing(false);
                    m_pingButton.setText(getResources().getString(R.string.start_ping));
                    setBtnEnabled(true);
                } else {
                    m_view.startPing();
                    m_pingButton.setText(getResources().getString(R.string.stop_ping));
                    setBtnEnabled(false);
                }
            }
        });

        m_interval = 100;
        m_lbInterval = (TextView)findViewById(R.id.lbInterval);
        m_lbIntervalTile = (TextView)findViewById(R.id.lbIntervalTitle);

        updateBatchIntervalLabel();

        m_up = (ImageButton) findViewById(R.id.btnUp);
        m_up.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                m_interval += 100;
                if (m_interval > 1000) {
                    m_interval = 1000;
                }
                updateBatchIntervalLabel();
            }
        });

        m_down = (ImageButton) findViewById(R.id.btnDown);
        m_down.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                m_interval -= 100;
                if (m_interval < 100) {
                    m_interval = 100;
                }
                updateBatchIntervalLabel();
            }
        });

        CheckBox cb = (CheckBox)findViewById(R.id.cbPingMode);
        cb.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                updateUpDownBtn();
            }
        });

        updateUpDownBtn();

        if (BLEHandler.getInstance().getConnectionCount() > 0) {
            setStatus("Connected");
        } else {
            setStatus("Not Connected");
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_test, menu);
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

    @Override
    protected void onResume() {
        super.onResume();
        m_view.registerBLEReceiver();
    }

    @Override
    protected void onPause() {
        super.onPause();

        if (m_view.isPing()) {
            m_view.stopPing(true);
        }
        m_view.unRegisterBLEReceiver();
        BLEHandler.getInstance().disconnect();
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            exit();
            return true;
        } else {
            return super.onKeyDown(keyCode, event);
        }
    }

    private void exit() {
        new AlertDialog.Builder(TestActivity.this).setTitle("Warning").setMessage("Do you want to exit?").setPositiveButton("Yes", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                finish();
                System.exit(0);
            }
        }).setNegativeButton("No", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                //
            }
        }).show();
    }

    public int getBatchInterval() {
        return m_interval;
    }

    private void updateBatchIntervalLabel() {
        m_lbInterval.setText(String.valueOf(m_interval));
    }

    public boolean isPingPongMode() {
        CheckBox cb = (CheckBox)findViewById(R.id.cbPingMode);
        return cb.isChecked();
    }

    private void setBtnEnabled(boolean isEnabled) {
        findViewById(R.id.cbPingMode).setEnabled(isEnabled);

        if (!isEnabled) {
            m_up.setEnabled(false);
            m_down.setEnabled(false);
            m_lbInterval.setEnabled(false);
        } else {
            updateUpDownBtn();
        }
    }

    private void updateUpDownBtn() {
        boolean isChecked = isPingPongMode();

        if (isChecked) {
            m_up.setEnabled(false);
            m_down.setEnabled(false);
            m_lbInterval.setEnabled(false);

            m_lbIntervalTile.setVisibility(View.INVISIBLE);
            m_lbInterval.setVisibility(View.INVISIBLE);
            m_up.setVisibility(View.INVISIBLE);
            m_down.setVisibility(View.INVISIBLE);
        } else {
            m_up.setEnabled(true);
            m_down.setEnabled(true);
            m_lbInterval.setEnabled(true);

            m_lbIntervalTile.setVisibility(View.VISIBLE);
            m_lbInterval.setVisibility(View.VISIBLE);
            m_up.setVisibility(View.VISIBLE);
            m_down.setVisibility(View.VISIBLE);
        }
    }

    public void setPingButtonEnabled(boolean enabled) {
        m_pingButton.setEnabled(enabled);
    }

    public void setStatus(String s) {
        TextView v = (TextView)findViewById(R.id.lbNetworkStatus);
        v.setText(s);
    }
}
