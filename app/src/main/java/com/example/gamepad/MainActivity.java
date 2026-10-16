package com.example.gamepad;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;

public class MainActivity extends Activity {
    private DatagramSocket udpSocket;
    private InetAddress serverAddress;
    private int serverPort = 5005;
    private String targetIp = "192.168.0.10"; // PC IP 기본값

    private FrameLayout gameLayout;
    private boolean isEditMode = false;
    private View selectedButton = null;
    private float dX, dY;

    @SuppressLint("ClickableViewAccessibility")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // 전체 화면 레이아웃 구성
        FrameLayout rootLayout = new FrameLayout(this);
        rootLayout.setBackgroundColor(Color.TRANSPARENT);

        // 게임 화면이 깔릴 베이스 레이아웃 (투명)
        gameLayout = new FrameLayout(this);
        rootLayout.addView(gameLayout, new FrameLayout.LayoutParams(-1, -1));

        // 상단 컨트롤 바 (설정/편집 모드 전환용)
        LinearLayout topBar = new LinearLayout(this);
        topBar.setOrientation(LinearLayout.HORIZONTAL);
        topBar.setPadding(20, 20, 20, 20);

        Button btnEdit = new Button(this);
        btnEdit.setText("⚙️ 편집/잠금");
        topBar.addView(btnEdit);

        Button btnAdd = new Button(this);
        btnAdd.setText("➕ 버튼추가");
        topBar.addView(btnAdd);

        SeekBar seekBarOpacity = new SeekBar(this);
        seekBarOpacity.setMax(100);
        seekBarOpacity.setProgress(60);
        seekBarOpacity.setLayoutParams(new LinearLayout.LayoutParams(300, -2));
        topBar.addView(seekBarOpacity);

        TextView tvInfo = new TextView(this);
        tvInfo.setText(" IP:5005 ");
        tvInfo.setTextColor(Color.WHITE);
        topBar.addView(tvInfo);

        rootLayout.addView(topBar, new FrameLayout.LayoutParams(-2, -2));
        setContentView(rootLayout);

        // 백그라운드 네트워크 전송 스레드 시작
        new Thread(() -> {
            try {
                udpSocket = new DatagramSocket();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();

        // 투명도 조절바 리스너
        seekBarOpacity.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                float alpha = (float) progress / 100f;
                for (int i = 0; i < gameLayout.getChildCount(); i++) {
                    gameLayout.getChildAt(i).setAlpha(alpha);
                }
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        // 편집 모드 토글
        btnEdit.setOnClickListener(v -> {
            isEditMode = !isEditMode;
            btnEdit.setText(isEditMode ? "🔒 잠금(게임)" : "⚙️ 편집/잠금");
            selectedButton = null;
        });

        // 새 버튼 추가 (기본값: RT+A 조합키 예시)
        btnAdd.setOnClickListener(v -> addGamepadButton("RT+A", 300, 300, 200, 120));

        // 기본 기본 패드 버튼들 미리 생성 (방향키, A, B, RT 등)
        addGamepadButton("UP", 150, 400, 150, 150);
        addGamepadButton("DOWN", 150, 700, 150, 150);
        addGamepadButton("LEFT", 50, 550, 150, 150);
        addGamepadButton("RIGHT", 250, 550, 150, 150);

        addGamepadButton("A", 1600, 600, 160, 160);
        addGamepadButton("B", 1800, 450, 160, 160);
        addGamepadButton("RT", 1600, 200, 200, 120);
        addGamepadButton("LB+RT", 1350, 200, 200, 120);
    }

    @SuppressLint("ClickableViewAccessibility")
    private void addGamepadButton(String cmdName, int x, int y, int w, int h) {
        Button btn = new Button(this);
        btn.setText(cmdName);
        btn.setTextColor(Color.WHITE);
        btn.setBackgroundColor(Color.parseColor("#44FFFFFF")); // 반투명 테두리 느낌
        btn.setAlpha(0.6f);

        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(w, h);
        params.leftMargin = x;
        params.topMargin = y;
        gameLayout.addView(btn, params);

        // 터치 및 드래그 이벤트 처리
        btn.setOnTouchListener((v, event) -> {
            if (isEditMode) {
                // 편집 모드: 위치 이동 또는 크기 조절
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        dX = v.getX() - event.getRawX();
                        dY = v.getY() - event.getRawY();
                        selectedButton = v;
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        if (selectedButton == v) {
                            v.animate()
                                    .x(event.getRawX() + dX)
                                    .y(event.getRawY() + dY)
                                    .setDuration(0)
                                    .start();
                        }
                        return true;
                }
            } else {
                // 게임 모드: 누를 때 / 뗄 때 UDP로 PC 서버에 즉시 신호 전송
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        v.setPressed(true);
                        sendUdp("COMBO:" + cmdName + ":DOWN");
                        return true;
                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        v.setPressed(false);
                        sendUdp("COMBO:" + cmdName + ":UP");
                        return true;
                }
            }
            return false;
        });
    }

    private void sendUdp(String msg) {
        new Thread(() -> {
            try {
                if (udpSocket == null) return;
                // ※ 주의: 집에서 PC의 IP 주소를 확인한 뒤 아래 targetIp를 본인 PC IP로 수정하면 됩니다!
                InetAddress addr = InetAddress.getByName("192.168.0.10"); 
                byte[] data = msg.getBytes();
                DatagramPacket packet = new DatagramPacket(data, data.length, addr, serverPort);
                udpSocket.send(packet);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }
}
