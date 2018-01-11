package ubknights.com.spaceshooter;

import android.annotation.TargetApi;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Point;
import android.graphics.Rect;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.media.SoundPool;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.util.SparseIntArray;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;

public class MainActivity extends Activity {
    SoundPool sound_pool;
    SparseIntArray soundMap;
    MediaPlayer mp;
    boolean isplaying=false;
    int successID=0,gameoverID=0;

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_main);
        //sound load

    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        //create a menu with two options: play round 1 or play round two
        super.onCreateOptionsMenu(menu);
        getMenuInflater().inflate(R.menu.menu,menu);
       // menu.add(Menu.NONE,0,Menu.NONE,"fullscreen");
        return true;
    }
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {

        switch (item.getItemId()) {
            case R.id.round1:
                //start round1
                //sound_pool.autoPause();
                if (isplaying) mp.pause();
               Intent t = new Intent(MainActivity.this, TheGameLevel1X.class);
                startActivityForResult(t,0);
                return true;
            case R.id.round2:
                //start round2
                //sound_pool.autoPause();
                if (isplaying) mp.pause();
                Intent t2 = new Intent(MainActivity.this, TheGameLevel2.class);
                startActivityForResult(t2,1);
                return true;
            default:
                return super.onOptionsItemSelected(item);

        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (resultCode==1){
            long nano=data.getLongExtra("Time",0);
            int seconds=(int)(nano/1000000000);
            int minutes = seconds / 60;
            seconds = seconds % 60;
            int status=data.getIntExtra("Status",-1);
            if (status==1){
                //successID=sound_pool.play(1,  1, 1, 1 , -1, 1);
                mp = MediaPlayer.create(MainActivity.this, R.raw.success);
                mp.setOnPreparedListener(okStart);
                mp.setOnCompletionListener(mainDone);
                AlertDialog.Builder builder=new AlertDialog.Builder(MainActivity.this);
                builder.setTitle("You have finished round 1!!");
                builder.setMessage(String.format("Time finished: %d:%02d", minutes, seconds));
                builder.setPositiveButton("OK", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        if (isplaying) {
                            mp.pause();
                            isplaying=false;
                        }
                    }
                });
                builder.create().show();
            }else if (status==0){
                //sound_pool.play(2,  1, 1, 1 , -1, 1);
                mp = MediaPlayer.create(MainActivity.this, R.raw.gameover);
                mp.setOnPreparedListener(okStart);
                mp.setOnCompletionListener(mainDone);
                AlertDialog.Builder builder=new AlertDialog.Builder(MainActivity.this);
                builder.setTitle("Your ship has exploded!!");
                builder.setMessage(String.format("Time survived: %d:%02d", minutes, seconds));
                builder.setPositiveButton("OK", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        if (isplaying) mp.pause();
                    }
                });
                builder.create().show();
            }
        }
        else if (resultCode==2){
            long nano=data.getLongExtra("Time",0);
            int seconds=(int)(nano/1000000000);
            int minutes = seconds / 60;
            seconds = seconds % 60;
            int status=data.getIntExtra("Status",-1);
            if (status==1){
                mp = MediaPlayer.create(MainActivity.this, R.raw.success);
                mp.setOnPreparedListener(okStart);
                mp.setOnCompletionListener(mainDone);
                AlertDialog.Builder builder=new AlertDialog.Builder(MainActivity.this);
                builder.setTitle("You have finished round 2!!");
                builder.setMessage(String.format("Time finished: %d:%02d", minutes, seconds));
                builder.setPositiveButton("OK", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        if (isplaying) mp.pause();
                    }
                });
                builder.create().show();
            }
            else if (status==0){
                mp = MediaPlayer.create(MainActivity.this, R.raw.gameover);
                mp.setOnPreparedListener(okStart);
                mp.setOnCompletionListener(mainDone);
                AlertDialog.Builder builder=new AlertDialog.Builder(MainActivity.this);
                builder.setTitle("Your ship has exploded!!");
                builder.setMessage(String.format("Time survived: %d:%02d", minutes, seconds));
                builder.setPositiveButton("OK", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        if (isplaying) mp.pause();
                    }
                });
                builder.create().show();
            }

        }
    }
    private MediaPlayer.OnCompletionListener mainDone = new MediaPlayer.OnCompletionListener() {

        @Override
        public void onCompletion(MediaPlayer mp) {
            isplaying = false;
            //or set an endless loop
            //mp.seekTo(0);
            //mp.start();
        }
    };
    private MediaPlayer.OnPreparedListener okStart = new MediaPlayer.OnPreparedListener() {

        @Override
        public void onPrepared(MediaPlayer mp) {
            mp.start();
            isplaying = true;
        }
    };
}
