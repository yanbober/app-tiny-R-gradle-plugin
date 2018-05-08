package cn.yan.app;

import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.widget.TextView;

import cn.yan.demo.reslib.DemoLib;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        StringBuilder builder = new StringBuilder();

        String name = getResources().getString(R.string.app_name);
        builder.append(name).append("\n");

        String copy = DemoLib.getAndridCopy(this);
        String t = DemoLib.getStringT(this);
        String age = new DemoLib().getStringAge(this);
        builder.append(copy).append("\n");
        builder.append(t).append("\n");
        builder.append(age).append("\n");
        TextView textView = findViewById(R.id.app_name);
        textView.setText(builder.toString());
    }
}
