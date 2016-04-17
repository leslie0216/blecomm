package com.nclab.chl848.blecomm;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;

public class TestActivity extends Activity {

    TestView m_view;
    private Button m_pingBtn;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_test);

        m_view = new TestView(this);
        this.addContentView(m_view, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.MATCH_PARENT));

        m_pingBtn = new Button(this);
        m_pingBtn.setText(getResources().getString(R.string.start_ping));
        m_pingBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (m_view.isPing()) {
                    m_view.stopPing(false);
                    m_pingBtn.setText(getResources().getString(R.string.start_ping));
                } else {
                    m_view.startPing();
                    m_pingBtn.setText(getResources().getString(R.string.stop_ping));
                }
            }
        });

        RelativeLayout relativeLayout = new RelativeLayout(this);

        RelativeLayout.LayoutParams layoutParams = new RelativeLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        layoutParams.addRule(RelativeLayout.ALIGN_PARENT_TOP);
        layoutParams.addRule(RelativeLayout.ALIGN_PARENT_RIGHT);
        relativeLayout.addView(m_pingBtn, layoutParams);
        setPingButtonEnabled(BLEHandler.getInstance().getConnectionCount() > 0);

        this.addContentView(relativeLayout, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));

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

    public void setPingButtonEnabled(boolean enabled) {
        m_pingBtn.setEnabled(enabled);
    }
}
