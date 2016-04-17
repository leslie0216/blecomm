package com.nclab.chl848.blecomm;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;

public class ConnectionActivity extends Activity {

    private ConnectionView m_view;
    private Button m_doneBtn;
    private Button m_scanBtn;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_connection);

        m_view = (ConnectionView)findViewById(R.id.conn_view);
        //m_view = new ConnectionView(this);
        //this.addContentView(m_view, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.MATCH_PARENT));

        m_doneBtn = new Button(this);
        m_doneBtn.setText(getResources().getString(R.string.done));
        m_doneBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (BLEHandler.getInstance().isCentral()) {
                    BLEHandler.getInstance().stopScan();
                } else {
                    BLEHandler.getInstance().setIsAdvertise(false);
                }
                Intent intent = new Intent();
                intent.setClass(ConnectionActivity.this, TestActivity.class);
                ConnectionActivity.this.startActivity(intent);
                ConnectionActivity.this.finish();
            }
        });

        RelativeLayout relativeLayout = new RelativeLayout(this);

        RelativeLayout.LayoutParams layoutParams = new RelativeLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        layoutParams.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM);
        layoutParams.addRule(RelativeLayout.ALIGN_PARENT_LEFT);
        relativeLayout.addView(m_doneBtn, layoutParams);
        m_doneBtn.setEnabled(false);

        this.addContentView(relativeLayout, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        Button backBtn = new Button(this);
        backBtn.setText(getResources().getString(R.string.back));
        backBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                BLEHandler.getInstance().disconnect();

                Intent intent = new Intent();
                intent.setClass(ConnectionActivity.this, SettingActivity.class);
                ConnectionActivity.this.startActivity(intent);
                ConnectionActivity.this.finish();
            }
        });

        RelativeLayout relativeLayout_con = new RelativeLayout(this);

        RelativeLayout.LayoutParams layoutParams_con = new RelativeLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        layoutParams_con.addRule(RelativeLayout.ALIGN_PARENT_TOP);
        layoutParams_con.addRule(RelativeLayout.ALIGN_PARENT_LEFT);
        relativeLayout_con.addView(backBtn, layoutParams_con);

        this.addContentView(relativeLayout_con, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));


        m_scanBtn = new Button(this);
        if (BLEHandler.getInstance().isCentral()) {
            m_scanBtn.setText(getResources().getString(R.string.start_scan));
        } else {
            m_scanBtn.setText(getResources().getString(R.string.start_adverting));
        }
        m_scanBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (BLEHandler.getInstance().isScanningOrAdvertising()) {
                    if (BLEHandler.getInstance().isCentral()) {
                        BLEHandler.getInstance().stopScan();
                        m_scanBtn.setText(getResources().getString(R.string.start_scan));
                    } else {
                        BLEHandler.getInstance().setIsAdvertise(false);
                        m_scanBtn.setText(getResources().getString(R.string.start_adverting));
                    }
                } else {
                    if (BLEHandler.getInstance().isCentral()) {
                        BLEHandler.getInstance().startScan();
                        m_scanBtn.setText(getResources().getString(R.string.stop_scan));
                    } else {
                        BLEHandler.getInstance().setIsAdvertise(true);
                        m_scanBtn.setText(getResources().getString(R.string.stop_adverting));
                    }
                }
            }
        });

        RelativeLayout relativeLayout_start = new RelativeLayout(this);

        RelativeLayout.LayoutParams layoutParams_start = new RelativeLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        layoutParams_start.addRule(RelativeLayout.ALIGN_PARENT_TOP);
        layoutParams_start.addRule(RelativeLayout.ALIGN_PARENT_RIGHT);
        relativeLayout_start.addView(m_scanBtn, layoutParams_start);

        this.addContentView(relativeLayout_start, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));
    }

    public void enableDoneButton() {
        m_doneBtn.setEnabled(true);
    }

    public void disableDoneButton(){
        m_doneBtn.setEnabled(false);
    }

    public void resetScanButton() {
        if (BLEHandler.getInstance().isCentral()) {
            m_scanBtn.setText(getResources().getString(R.string.start_scan));
        } else {
            m_scanBtn.setText(getResources().getString(R.string.start_adverting));
        }
    }

    public void disableScanButton() {
        m_scanBtn.setEnabled(false);
    }

    @Override
    protected void onResume() {
        super.onResume();
        m_view.registerBLEReceiver();
        if (!BLEHandler.getInstance().getIsInit()) {
            BLEHandler.getInstance().setCurrentActivity(ConnectionActivity.this);

            BLEHandler.getInstance().setup();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();

        m_view.unRegisterBLEReceiver();
    }
}
