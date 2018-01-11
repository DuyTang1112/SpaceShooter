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

public class TheGameLevel2 extends Activity implements View.OnTouchListener{
    MediaPlayer mp;
    SoundPool sound_pool;
    SparseIntArray soundMap;
    TheView mySurfaceView;      //the surfaceview, where we draw
    TheSprites2 allsprites; //class that has all the location and sizes of the images in the sprite
    float xTouch,yTouch;          //the location when the screen is touched
    int s_width,s_height;       //the size of the surfaceview
    int shipheight,shipwidth,meteorheight,meteorwidth;
    //used for which sprite to use
    int loc = 0,eLoc=0;
    int[] mloc; //use for meteor sprite
    int[] explodeMloc; //use for explosion sprite
    Point shipbullet;
    Point[] meteor;
    float[] meteorSlope;
    boolean[] meteorIsSpawn, isExploding;
    byte[] meteorHitCount;
    byte numMeteor,score; // count number of spawn meteor/ meteor hits
    //15 frames per seconds
    float skipTime =1000.0f/35.0f; //setting 120fps
    long lastUpdate,startTime,moveTime;
    float dt;
    int bulletSpeed, meteorSpeed,prevYtouch;
    boolean gameover,gethit;
    Vibrator vibrator;
    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        //setContentView(R.layout.spritesheetbetter); //not setting a static xml
        //set all the sprite locations and sizes
        allsprites = new TheSprites2(getResources());
        //make sure there is only ONE copy of the image and that the image
        //is in the drawable-nodpi. if it is not unwanted scaling might occur
        prevYtouch=shipheight=shipwidth=0;
        shipbullet = new Point(); //used for canvas drawing location
        moveTime=lastUpdate = 0;         //to check against now time
        meteor=new Point[3];        // store meteor's coordinates
        for (int i=0;i<meteor.length;i++){
            meteor[i]=new Point();
        }
        meteorSlope=new float[3];
        for (int i=0;i<meteorSlope.length;i++){
            meteorSlope[i]=0;
        }
        mloc=new int[3];
        explodeMloc=new int[3];
        for (int i=0;i<mloc.length;i++){
            mloc[i]=0;
            explodeMloc[i]=0;
        }
        meteorIsSpawn=new boolean[3]; // check if a meteor is spawned or not
        isExploding=new boolean[3]; // check if meteor is taking hit
        for (int i=0;i<meteorIsSpawn.length;i++){
            meteorIsSpawn[i]=false;
            isExploding[i]=false;
        }
        score=numMeteor=0;   // track the number of meteor spawned
        meteorHitCount=new byte[3]; //track the number of hit a meteor take
        for (int i=0;i<meteorHitCount.length;i++){
            meteorHitCount[i]=0;
        }
        gethit=gameover=false;
        vibrator=(Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
        //sound load
        AudioAttributes audioatts = new AudioAttributes.Builder().
                setUsage(AudioAttributes.USAGE_GAME).
                setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build();

        sound_pool =new SoundPool.Builder().setMaxStreams(10).setAudioAttributes(audioatts).build();
        soundMap = new SparseIntArray();

        //parameters - id,value(context,sound,priority)  priority = 1 not used
        soundMap.put(1, sound_pool.load(TheGameLevel2.this, R.raw.meteorboom, 1));//load returns id
        soundMap.put(2,sound_pool.load(TheGameLevel2.this, R.raw.shipboom, 1));
        soundMap.put(3,sound_pool.load(TheGameLevel2.this, R.raw.shipbullet, 1));
        //hide the actoinbar and make it fullscreen
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
                mp = MediaPlayer.create(TheGameLevel2.this, R.raw.mainsong);
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
                moveTime=System.currentTimeMillis();
                mySurfaceView.startGame();
            }
        },2000);


    }

    public void hideAndFull()
    {
        ActionBar bar = getActionBar();
        bar.hide();
        final View decorView = getWindow().getDecorView();
        decorView.setSystemUiVisibility(View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                |View.SYSTEM_UI_FLAG_HIDE_NAVIGATION |View.SYSTEM_UI_FLAG_FULLSCREEN);
    }

    @Override
    public boolean onTouch(View view, MotionEvent motionEvent) {
        switch(motionEvent.getAction())
        {
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
                    // Log.d("d", dt+" "+"latupdate: "+ lastUpdate);
                    if (dt >= skipTime) {
                        //look it to paint on it
                        Canvas c = holder.lockCanvas();
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
                        Rect place;
                        //*********Draw Meteor
                        for (int i=0;i<meteorIsSpawn.length;i++){
                            if (meteorIsSpawn[i]){
                                place = new Rect(meteor[i].x, meteor[i].y,
                                        meteor[i].x + meteorwidth,
                                        meteor[i].y + meteorheight);
                                c.drawBitmap(allsprites.space, allsprites.meteorsprites[mloc[i]], place, null);
                            }
                        }
                        //draw ship
                        if (!gethit) {
                            place = new Rect(0, (int) yTouch,
                                    shipwidth, (int) (yTouch + shipheight));
                            c.drawBitmap(allsprites.space, allsprites.shipSprites[eLoc], place, null);
                        }
                        //ship shooting
                        place = new Rect((int) shipbullet.x, shipbullet.y, (int) shipbullet.x + allsprites.shipBulletSprite.width() * 4,
                                shipbullet.y + allsprites.shipBulletSprite.height() * 4);
                        c.drawBitmap(allsprites.space, allsprites.shipBulletSprite, place, null);

                        // draw meteor explosion
                        for (int i=0;i<isExploding.length;i++){
                            if (isExploding[i]){
                                place=new Rect(meteor[i].x, meteor[i].y,
                                        meteor[i].x + meteorwidth,
                                        meteor[i].y + meteorheight);
                                c.drawBitmap(allsprites.space,allsprites.boomsprites[explodeMloc[i]],place,null);
                                explodeMloc[i]=(explodeMloc[i]+1)%6;
                                if (explodeMloc[i]==0){
                                    isExploding[i]=false;
                                    //at the end of animation check if game is finished
                                    if (score==10) gameDone();
                                }
                            }
                        }
                        // draw ship explosion
                        if (gethit){
                            place=new Rect(0,prevYtouch,shipwidth,prevYtouch+shipheight);
                            c.drawBitmap(allsprites.space,allsprites.boomsprites[loc],place,null);
                            loc = ((loc + 1) % 6);
                            if (loc==0){
                                gameover=true;
                                gameDone();
                            }
                        }


                        //move bullets, meteor and update sprites
                        shipbullet.x += bulletSpeed;
                        //move meteor
                        for (int i=0;i<meteorIsSpawn.length;i++){
                            if (meteorIsSpawn[i]){
                                meteor[i].x=meteor[i].x-meteorSpeed;
                                meteor[i].y=(int)(meteor[i].y-meteorSlope[i]*meteorSpeed);
                            }
                        }
                        // update sprites

                        eLoc = (eLoc + 1) % 4;

                        //update sprites for meteor
                        for (int i=0;i<meteorIsSpawn.length;i++){
                            if (meteorIsSpawn[i]){
                                mloc[i]=(mloc[i]+1)%4;
                            }
                        }

                        //check if bullet hit meteors
                        checkHitEnemies();

                        //check if meteor hit the ship
                        for (int i=0;i<meteorIsSpawn.length;i++){
                            if(meteorIsSpawn[i]){
                                if (shipwidth>=meteor[i].x&&0<=meteor[i].x+meteorwidth
                                        &&yTouch+shipheight>=meteor[i].y&&yTouch<=meteor[i].y+meteorheight){
                                    gethit=true;
                                    sound_pool.play(2,  1, 1, 1 , 0, 1);
                                    vibrator.vibrate(500);
                                    prevYtouch=(int)yTouch;
                                    break;
                                }
                            }
                        }

                        //check if
                        //check if ship bullet is out of screen
                        if (shipbullet.x > s_width) {
                            resetShipBullet();
                        }
                        // check if meteors are out of screen
                        for (int i=0;i<meteorIsSpawn.length;i++){
                            if (meteorIsSpawn[i]){
                                if (meteor[i].x+meteorwidth<0||meteor[i].y+meteorheight<0||meteor[i].y>s_height){
                                    meteorIsSpawn[i]=false;
                                    numMeteor--;
                                }
                            }
                        }
                        //spawn new meteor after 3 sec
                        Log.d("Meteor check",meteorIsSpawn[0]+" "+meteorIsSpawn[1]+" "+meteorIsSpawn[2]+" ");
                        Log.d("num meteor",numMeteor+"");
                        // spawn new meteor every 3 sec
                        if (System.currentTimeMillis()-moveTime>=3000) {
                            moveEnemy();
                            moveTime=System.currentTimeMillis();
                        }
                        lastUpdate = System.currentTimeMillis();
                        holder.unlockCanvasAndPost(c);
                    }
                }
            }
        };

        public void resetShipBullet()
        {
            sound_pool.play(3,  1, 1, 1 , 0, 1);
            shipbullet.y = (int) (yTouch + shipheight/2);
            shipbullet.x = (int) (shipwidth);
        }
        public void checkHitEnemies()
        {
            //check bullet for meteor
            for (int i=0;i<meteorIsSpawn.length;i++){
                if (meteorIsSpawn[i]){
                    if (shipbullet.x>=meteor[i].x&&
                            shipbullet.x<=meteor[i].x+meteorwidth&&
                            shipbullet.y>=meteor[i].y&&
                            shipbullet.y<=meteor[i].y+meteorheight){
                        meteorHitCount[i]++;
                        isExploding[i]=true;
                        explodeMloc[i]=0;
                        resetShipBullet();
                        sound_pool.play(1,  1, 1, 1 , 0, 1);
                        vibrator.vibrate(500);
                        if (meteorHitCount[i]==3) {
                            meteorHitCount[i]=0;
                            meteorIsSpawn[i]=false;
                            numMeteor--;
                            score++;
                        }
                    }
                }
            }

        }
        public void moveEnemy()//set a new random location after 1 second
        {
            Random r = new Random();
            float rand;
            if (numMeteor==3) return;
            for (int i=0;i<3;i++){
                if (!meteorIsSpawn[i]){
                    Log.d("Spawn meteor",i+"");
                    meteorIsSpawn[i]=true;
                    //set initial position
                    rand = r.nextFloat();
                    while (rand < .7f || rand > .85f) {
                        rand = r.nextFloat();
                    }
                    meteor[i].x=(int)(s_width*rand);
                    rand = r.nextFloat();
                    while ( rand > .8f) {
                        rand = r.nextFloat();
                    }
                    meteor[i].y=(int)(s_height*rand);
                    meteorSlope[i]=((float)(meteor[i].y-yTouch))/((float)(meteor[i].x-xTouch));
                    mloc[i]=0;
                    numMeteor++;
                    break;
                }
                else continue;
            }
        }
        public void startGame()
        {
            gameThread.start();
        }
        public void gameDone(){
            change = false;
            //clean the surface and show the menu by removing fullscreen
            fin();
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

            //setting ship width and height
            shipwidth=(int)(s_width * 0.1);
            shipheight=(int)(s_height * 0.1);
            // setting ship bullet initial position
            shipbullet.y = (int) (yTouch+shipheight/ 2);
            shipbullet.x = (int) (shipwidth);
            //setting meteor width and height
            meteorwidth=(int)(s_width*.15f);
            meteorheight=meteorwidth;
            // set bullet speed and meteor speed
            bulletSpeed=s_width/20;
            meteorSpeed=s_width/140;
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
        else if(gameover) intent.putExtra("Status",0);
        intent.putExtra("Time",System.nanoTime()-startTime);
        setResult(2,intent);
        finish();
    }


}
