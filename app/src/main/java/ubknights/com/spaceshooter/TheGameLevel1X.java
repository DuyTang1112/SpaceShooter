package ubknights.com.spaceshooter;

import android.annotation.TargetApi;
import android.app.ActionBar;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.media.SoundPool;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Vibrator;
import android.util.Log;
import android.util.SparseIntArray;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;

import java.util.Random;

/**
 * Created by carlos on 10/13/2016.
 * modified by Duy Tang
 */

public class TheGameLevel1X extends Activity implements View.OnTouchListener {
    MediaPlayer mp;
    TheView mySurfaceView;      //the surfaceview, where we draw
    TheSprites2 allsprites; //class that has all the location and sizes of the images in the sprite
    float xTouch, yTouch;          //the location when the screen is touched
    int s_width, s_height;       //the size of the surfaceview
    int enemywidth, enemyheight,shipwidth,shipheight;
    //used for which sprite to use
    int loc = 0,loc2=0,locship=0, eLoc = 0, e1Loc = 3, e2Loc = 0;//, m1Loc = 0, m2Loc = 1, m3Loc = 2;
    Point shipbullet, enemy1, enemy2, enemy1bullet, enemy2bullet;//, meteor1, meteor2, meteor3;
    Point prevExplosion1,prevExplosion2;
    int prevShipY;
    float skipTime = 1000.0f / 35.0f; //setting 480fps
    long lastUpdate,moveTime,startTime;
    float dt;
    boolean hitEnemy1=false,hitEnemy2=false,getHit=false;
    byte score,hitCount;
    boolean start;
    SoundPool sound_pool;
    SparseIntArray soundMap;
    Vibrator vibrator;
    int prevStreamID=0;
    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        //setContentView(R.layout.spritesheetbetter); //not setting a static xml
        //set all the sprite locations and sizes
        allsprites = new TheSprites2(getResources());
        //make sure there is only ONE copy of the image and that the image
        //is in the drawable-nodpi. if it is not unwanted scaling might occur
        shipbullet = new Point(); //used for canvas drawing location
        enemy1 = new Point();     //used for canvas drawing location
        enemy1bullet = new Point();
        enemy2 = new Point();
        enemy2bullet = new Point();
        moveTime = lastUpdate = 0;         //to check against now time
        prevExplosion1=new Point();
        prevExplosion2=new Point();
        score=hitCount=0;
        start=true;
        vibrator=(Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
        //sound load
        AudioAttributes audioatts = new AudioAttributes.Builder().
                setUsage(AudioAttributes.USAGE_GAME).
                setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build();

        sound_pool =new SoundPool.Builder().setMaxStreams(10).setAudioAttributes(audioatts).build();
        soundMap = new SparseIntArray();

        //parameters - id,value(context,sound,priority)  priority = 1 not used
        soundMap.put(1, sound_pool.load(TheGameLevel1X.this, R.raw.enemyboom, 1));//load returns id
        soundMap.put(2,sound_pool.load(TheGameLevel1X.this, R.raw.shipboom, 1));
        soundMap.put(3,sound_pool.load(TheGameLevel1X.this, R.raw.shipbullet, 1));
        //hide the actionnbar and make it fullscreen
        hideAndFull();
        //our custom view
        mySurfaceView = new TheView(this);
        mySurfaceView.setOnTouchListener(this); //now we can touch the screen
        setContentView(mySurfaceView);

        Handler h = new Handler();
        h.postDelayed(new Runnable() {
            @Override
            public void run() {
                // set background music
                mp = MediaPlayer.create(TheGameLevel1X.this, R.raw.mainsong);
                mp.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
                    @Override
                    public void onPrepared(MediaPlayer mp) {
                        mp.start();
                    }
                });//invokes when is ready to start,
                mp.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
                    @Override
                    public void onCompletion(MediaPlayer mp) {
                        mp.seekTo(0);
                        mp.start();
                    }
                });//invokes when is reaches the end
                startTime=System.nanoTime();
                mySurfaceView.startGame();
            }
        }, 2000);


    }

    public void hideAndFull() {
        ActionBar bar = getActionBar();
        bar.hide();
        final View decorView = getWindow().getDecorView();
        decorView.setSystemUiVisibility(View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_FULLSCREEN);
    }

    @Override
    public boolean onTouch(View view, MotionEvent motionEvent) {
        switch (motionEvent.getAction()) {
            case MotionEvent.ACTION_DOWN:
                xTouch = motionEvent.getX();
                yTouch = motionEvent.getY();
                break;
            case MotionEvent.ACTION_UP:
                xTouch = motionEvent.getX();
                yTouch = motionEvent.getY();
                view.performClick();//to get rid of the message, mimicking a click
                break;
            case MotionEvent.ACTION_MOVE:
                xTouch = motionEvent.getX();
                yTouch = motionEvent.getY();
                break;
        }
        return true;
    }

    //surface view used so we can draw is dedicated made for drawing
    //View is updated in main thread while SurfaceView is updated in another thread.
    public class TheView extends SurfaceView implements SurfaceHolder.Callback {
        //resize and edit pixels in a surface. Holds the display
        SurfaceHolder holder;
        Boolean change = true;
        Thread gameThread;
        Canvas c;

        public TheView(Context context) {
            super(context);
            //get this holder
            holder = getHolder();//gets the surfaceview surface
            holder.addCallback(this);
            gameThread = new Thread(runn);
        }

        Runnable runn = new Runnable() {
            @Override
            public void run() {

                while (change == true) {
                    //perform drawing, does it have a surface?
                    if (!holder.getSurface().isValid()) {
                        continue;
                    }

                    dt = System.currentTimeMillis() - lastUpdate;
                    //Log.d("d", dt + " " + "last update: " + lastUpdate);
                    if (dt >= skipTime) {
                        //look it to paint on it
                        c = holder.lockCanvas();
                        //draw the background color
                        c.drawARGB(255, 0, 0, 0);
                        //**draw the background
                        Bitmap background = BitmapFactory.decodeResource(getResources(), R.drawable.background);
                        background = Bitmap.createScaledBitmap(background, s_width, s_height, true);
                        c.drawBitmap(background, 0, 0, null);
                        //** draw the timer
                        long nano=System.nanoTime()-startTime;
                        int seconds = (int) ( nano/ 1000000000);
                        int minutes = seconds / 60;
                        seconds = seconds % 60;

                        Paint p=new Paint();
                        p.setColor(Color.WHITE);
                        p.setTextSize(50);
                        int textWidth= (int) p.measureText(String.format("Time: %d:%02d", minutes, seconds));
                        c.drawText(String.format("Time: %d:%02d", minutes, seconds),s_width-textWidth,p.getTextSize()+5,p);
                        //draw ship
                        // Rect place = new Rect((int) xTouch, s_height - allsprites.shipSize.height() * 4,
                        //       (int) xTouch + allsprites.shipSize.width() * 4, (s_height - allsprites.shipSize.height() * 4) + allsprites.shipSize.height() * 4);
                        Rect place;
                        if (start){
                            place = new Rect(0, s_height/2,
                                    shipwidth, (int) (s_height/2 + shipheight));
                            c.drawBitmap(allsprites.space, allsprites.shipSprites[eLoc], place, null);
                            start=false;
                        }
                        else if (hitCount!=5&&!start){
                            place = new Rect(0, (int) yTouch,
                                    shipwidth, (int) (yTouch + shipheight));
                            c.drawBitmap(allsprites.space, allsprites.shipSprites[eLoc], place, null);
                        }

                        //draw the enemy 1
                        place = new Rect(enemy1.x, enemy1.y, enemy1.x + enemywidth,
                                enemy1.y + enemyheight);
                        c.drawBitmap(allsprites.space, allsprites.enemy1sprites[e1Loc], place, null);
                        //draw the enemy 2
                        place = new Rect(enemy2.x, enemy2.y, enemy2.x + enemywidth,
                                enemy2.y + enemyheight);
                        c.drawBitmap(allsprites.space, allsprites.enemy2sprites[e2Loc], place, null);
                        //ship shooting

                        place = new Rect((int) shipbullet.x, shipbullet.y, (int) shipbullet.x + allsprites.shipBulletSprite.width() * 4,
                                shipbullet.y + allsprites.shipBulletSprite.height() * 4);
                        c.drawBitmap(allsprites.space, allsprites.shipBulletSprite, place, null);
                        //enemy1 shooting
                        place = new Rect(enemy1bullet.x, enemy1bullet.y, enemy1bullet.x + allsprites.enemybulletSprite.width() * 4,
                                enemy1bullet.y + allsprites.enemybulletSprite.height() * 4);
                        c.drawBitmap(allsprites.space, allsprites.enemybulletSprite, place, null);
                        //enemy2 shooting
                        place = new Rect(enemy2bullet.x, enemy2bullet.y, enemy2bullet.x + allsprites.enemybulletSprite.width() * 4,
                                enemy2bullet.y + allsprites.enemybulletSprite.height() * 4);
                        c.drawBitmap(allsprites.space, allsprites.enemybulletSprite, place, null);


                        //move bullets and update sprites
                        shipbullet.x += s_width / 20;
                        //enemy1bullet
                        enemy1bullet.x -= s_width / 40;
                        //enemy2bullet
                        enemy2bullet.x -= s_width / 40;
                        // update index for bitmap animation

                        eLoc = (eLoc + 1) % 4;
                        e1Loc = (e1Loc + 1) % 6;
                        e2Loc = (e2Loc + 1) % 6;

                        //check if bullet hit enemy or meteors
                        checkHitEnemies();
                        //draw the explosion of enemy1
                        if (hitEnemy1){
                            place = new Rect(prevExplosion1.x, prevExplosion1.y, prevExplosion1.x+enemywidth ,
                                    prevExplosion1.y+enemyheight);
                            c.drawBitmap(allsprites.space, allsprites.boomsprites[loc], place, null);
                            loc = (loc + 1) % 6;
                            if (loc==0){
                                hitEnemy1=false;
                                // at the end of animation check whether game is finished
                                if (score==10) gameDone();
                            }
                        }
                        //draw the explosion of enemy2
                        if (hitEnemy2){
                            place = new Rect(prevExplosion2.x, prevExplosion2.y, prevExplosion2.x+enemywidth ,
                                    prevExplosion2.y+enemyheight);
                            c.drawBitmap(allsprites.space, allsprites.boomsprites[loc2], place, null);
                            loc2 = (loc2 + 1) % 6;
                            if (loc2==0){
                                hitEnemy2=false;
                                // at the end of animation check whether game is finished
                                if (score==10) gameDone();
                            }
                        }
                        //check if enemies bullet hit the ship
                        if (enemy1bullet.x<=shipwidth&&enemy1bullet.x>=0&&
                                enemy1bullet.y>=yTouch&&enemy1bullet.y<=yTouch+shipheight){
                            //play sound
                            //sound_pool.pause(prevStreamID);
                            sound_pool.play(2,  1, 1, 1 , 0, 1);
                            vibrator.vibrate(500);
                            hitCount++;
                            getHit=true;
                            prevShipY=(int)yTouch;
                            // reset the bullet from enemy1
                            resetEnemy1Bullet();

                        }
                        else if (enemy2bullet.x<=shipwidth&&enemy2bullet.x>=0&&
                                enemy2bullet.y>=yTouch&&enemy2bullet.y<=yTouch+shipheight){
                            sound_pool.play(2,  1, 1, 1 , 0, 1);
                            vibrator.vibrate(500);
                            hitCount++;
                            getHit=true;
                            prevShipY=(int)yTouch;
                            // reset the bullet from enemy2
                            resetEnemy2Bullet();

                        }
                        // draw the ship explosion if get hit
                        if (getHit){
                            place = new Rect(0, prevShipY, shipwidth,
                                    prevShipY+shipheight);
                            c.drawBitmap(allsprites.space, allsprites.boomsprites[locship], place, null);
                            locship = (locship + 1) % 6;
                            if (locship==0){
                                getHit=false;
                                // at the end of animation check whether game is finished
                                if (hitCount==5) gameDone();
                            }
                        }
                        //check if
                        //check if ship bullet is out of screen
                        if (shipbullet.x > s_width) {
                            resetShipBullet();
                        }
                        //check if enemy1 bullet is out of screen
                        if (enemy1bullet.x + allsprites.enemybulletSprite.width() * 4 < 0) {
                            resetEnemy1Bullet();
                        }
                        //check if enemy2 bullet is out of screen
                        if (enemy2bullet.x + allsprites.enemybulletSprite.width() * 4 < 0) {
                            resetEnemy2Bullet();
                        }
                        lastUpdate = System.currentTimeMillis();
                        // set new spawn location after 3 seconds
                        if (System.currentTimeMillis()-moveTime>=3000) {
                            moveEnemy();
                            moveTime=System.currentTimeMillis();
                        }
                        holder.unlockCanvasAndPost(c);
                    }

                }
            }
        };

        public void resetShipBullet() {   // for the ship
            sound_pool.play(3,  1, 1, 1 , 0, 1);
            shipbullet.y = (int) (yTouch + shipheight/2);
            shipbullet.x = (int) (shipwidth);
        }
        public void resetEnemy1Bullet(){

            enemy1bullet.x = enemy1.x - allsprites.enemybulletSprite.width() * 4;
            enemy1bullet.y = enemy1.y + enemyheight / 2;
        }
        public void resetEnemy2Bullet(){

            enemy2bullet.x = enemy2.x - allsprites.enemybulletSprite.width() * 4;
            enemy2bullet.y = enemy2.y + enemyheight / 2;
        }

        public void checkHitEnemies() {
            Random r = new Random();
            float rand;
            if (shipbullet.x >= enemy1.x && shipbullet.x <= enemy1.x + enemywidth &&
                    shipbullet.y >= enemy1.y && shipbullet.y <= enemy1.y + enemyheight) {
                //show explosion then spawn the enemy somewhere else and reset bullet
                //play sound
                sound_pool.play(1,  1, 1, 1 , 0, 1);
                hitEnemy1=true;
                score++;
                prevExplosion1.x=enemy1.x;
                prevExplosion1.y=enemy1.y;
                //showExplosion1();
                vibrator.vibrate(500);
                resetShipBullet();

                // spawn enemy 1 somewhere else
                // setting enemy 1's position
                rand = r.nextFloat();
                while (rand < .6f || rand > .9f) {
                    rand = r.nextFloat();
                }
                enemy1.x = (int) (s_width * rand);
                rand = r.nextFloat();
                while (rand > .9f) {
                    rand = r.nextFloat();
                }
                enemy1.y = (int) (s_height * rand);
            }
            //check bullt for enemy 2
            else if (shipbullet.x >= enemy2.x && shipbullet.x <= enemy2.x + enemywidth &&
                    shipbullet.y >= enemy2.y && shipbullet.y <= enemy2.y + enemyheight) {
                //show explosion then spawn the enemy somewhere else and reset bullet
                //play sound
                sound_pool.play(1,  1, 1, 1 , 0, 1);
                vibrator.vibrate(500);
                hitEnemy2=true;
                score++;
                prevExplosion2.x=enemy2.x;
                prevExplosion2.y=enemy2.y;
                //showExplosion2();
                resetShipBullet();
                //spawn enemy 2 somewhere else
                rand = r.nextFloat();
                while (rand < .6f || rand > .9f) {
                    rand = r.nextFloat();
                }
                enemy2.x = (int) (s_width * rand);

                rand = r.nextFloat();
                while (rand > .9f) {
                    rand = r.nextFloat();
                }

                enemy2.y = (int) (s_height * rand);
            }

            //else if

            //check bullet for meteor1
            //else if
            //check bullet for meteor2
            //else if
            //check bullet for meteor3
            //else if
        }



        public void moveEnemy()//set a new random location after 3 seconds
        {
            Random r = new Random();
            // setting enemy 1's position
            float rand = r.nextFloat();
            while (rand < .6f || rand > .9f) {
                rand = r.nextFloat();
            }

            enemy1.x = (int) (s_width * rand);

            rand = r.nextFloat();
            while (rand > .9f) {
                rand = r.nextFloat();
            }

            enemy1.y = (int) (s_height * rand);
            // setting enemy 2's position
            rand = r.nextFloat();
            while (rand < .6f || rand > .9f) {
                rand = r.nextFloat();
            }
            enemy2.x = (int) (s_width * rand);

            rand = r.nextFloat();
            while (rand > .9f) {
                rand = r.nextFloat();
            }

            enemy2.y = (int) (s_height * rand);
        }

        public void startGame() {
            gameThread.start();
        }

        public void gameDone() {
            change = false;
            //Intent intent=new Intent();
            fin();
            //clean the surface and show the menu by removing fullscreen
        }

        // three methods for the surfaceview
        @Override
        public void surfaceCreated(SurfaceHolder surfaceHolder) {

        }

        @Override
        public void surfaceChanged(SurfaceHolder surfaceHolder, int pixelFormat, int width, int height) {
            s_width = width;
            s_height = height;
            yTouch=s_height/2;
            // setting enemy ship's dimension
            enemywidth = (int) (s_width * .1f);
            enemyheight = (int) (s_height * .14f);
            shipwidth=(int)(s_width * 0.1);
            shipheight=(int)(s_height * 0.1);
            Random r = new Random();
            // setting enemy 1's position
            float rand = r.nextFloat();
            while (rand < .6f || rand > .9f) {
                rand = r.nextFloat();
            }

            enemy1.x = (int) (s_width * rand);

            rand = r.nextFloat();
            while (rand > .9f) {
                rand = r.nextFloat();
            }

            enemy1.y = (int) (s_height * rand);
            // setting enemy 2's position
            rand = r.nextFloat();
            while (rand < .6f || rand > .9f) {
                rand = r.nextFloat();
            }
            enemy2.x = (int) (s_width * rand);

            rand = r.nextFloat();
            while (rand > .9f) {
                rand = r.nextFloat();
            }

            enemy2.y = (int) (s_height * rand);

            // setting ship bullet initial position
            shipbullet.y = (int) (s_height/2+shipheight/ 2);
            shipbullet.x = (int) (shipwidth);
            //****Add  the enemy bullets
            //enemy 1:
            enemy1bullet.x = enemy1.x - enemywidth;
            enemy1bullet.y = enemy1.y + enemyheight / 2;
            //enemy2:
            enemy2bullet.x = enemy2.x - enemywidth;
            enemy2bullet.y = enemy2.y + enemyheight / 2;
        }

        @Override
        public void surfaceDestroyed(SurfaceHolder surfaceHolder) {

        }
    }

    @Override
    public void onBackPressed() {
        mySurfaceView.change=false;
        mp.pause();
        vibrator.cancel();
        sound_pool.release();
        super.onBackPressed();
    }

    private void fin() {
        mp.pause();
        vibrator.cancel();
        sound_pool.release();
        Intent intent=new Intent();
        if (score==10) intent.putExtra("Status",1);
        else if(hitCount==5) intent.putExtra("Status",0);
        intent.putExtra("Time",System.nanoTime()-startTime);
        setResult(1,intent);
        finish();
    }


}
