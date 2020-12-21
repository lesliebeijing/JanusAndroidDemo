package com.lesliefang.janusdemo2;

import android.Manifest;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import pub.devrel.easypermissions.EasyPermissions;

public class MainActivity extends AppCompatActivity {
    String[] perms = {Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO};
    EditText etUserName;
    Button btnStart;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main2);
        etUserName = findViewById(R.id.et_username);
        btnStart = findViewById(R.id.btn_start);

        btnStart.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String userName = etUserName.getText().toString().trim();
                if (userName.isEmpty()) {
                    Toast.makeText(MainActivity.this, "请输入用户名", Toast.LENGTH_SHORT).show();
                    return;
                }
                if (!EasyPermissions.hasPermissions(MainActivity.this, perms)) {
                    EasyPermissions.requestPermissions(MainActivity.this, "需要相机和录音权限",
                            100, perms);
                } else {
                    Intent intent = new Intent(MainActivity.this, VideoRoomActivity.class);
                    intent.putExtra("userName", userName);
                    startActivity(intent);
                }
            }
        });
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        EasyPermissions.onRequestPermissionsResult(requestCode, permissions, grantResults, this);
    }
}