import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.Random;

public class platform extends JPanel implements ActionListener, KeyListener {

    // ── Window ──────────────────────────────────────────────
    static final int W = 900, H = 550;

    // ── Physics (smoother, more forgiving) ──────────────────
    static final float GRAVITY      = 0.55f;   // lighter gravity
    static final float JUMP_VEL     = -14.5f;  // snappy jump
    static final float MOVE_SPD     = 5.5f;
    static final float ACCEL        = 0.85f;   // acceleration factor
    static final float FRICTION     = 0.78f;   // ground friction
    static final float AIR_FRICTION = 0.92f;   // air control
    static final float MAX_FALL     = 14f;
    static final int   COYOTE_TIME  = 8;       // frames after leaving ledge can still jump
    static final int   JUMP_BUFFER  = 8;       // frames jump input is buffered

    // ── Game States ─────────────────────────────────────────
    enum State { MENU, PLAYING, DEAD, WIN_LEVEL, WIN_GAME }
    State state = State.MENU;

    int currentLevel = 0;
    int totalScore   = 0;
    int deaths       = 0;

    // ── Player ───────────────────────────────────────────────
    float px = 80, py = 300, pvx = 0, pvy = 0;
    boolean onGround    = false;
    boolean facingRight = true;
    int playerW = 14, playerH = 36;
    int dotBobTick = 0;
    int coyoteTimer   = 0;  // coyote time counter
    int jumpBufferTimer = 0; // buffered jump counter
    boolean wasOnGround = false;

    // ── Input ────────────────────────────────────────────────
    boolean leftDown, rightDown, jumpDown, jumpConsumed;

    // ── Camera ───────────────────────────────────────────────
    float camX = 0;

    // ── Platform ─────────────────────────────────────────────
    static class Platform {
        int x, y, w, h;
        boolean fake;       // crumbles after step
        boolean bouncy;     // launches player
        boolean moving;     // moves left/right
        boolean invisible;  // invisible but solid
        float mx, my, mrange, mspeed, mdir = 1;
        int   fakeTimer = 0;
        boolean fakeTriggered = false;
        boolean revealTimer_active = false;
        int revealFlash = 0; // brief flash when player lands on invisible

        // Troll: platform shifts when player stands on it
        boolean shiftOnStep = false;
        boolean shiftTriggered = false;
        int shiftTimer = 0;
        int shiftDist = 0;

        Platform(int x, int y, int w, int h) {
            this.x = x; this.y = y; this.w = w; this.h = h;
        }
        Rectangle rect() { return new Rectangle(x, y, w, h); }
    }
    ArrayList<Platform> platforms = new ArrayList<>();

    // ── Hazards ──────────────────────────────────────────────
    static class Spike {
        int x, y, w, h;
        boolean chasing;      // troll: spike chases player
        float cx, cy, cspeed;
        boolean cdir = true;
        Spike(int x, int y, int w, int h){ this.x=x; this.y=y; this.w=w; this.h=h;
            this.cx=x; this.cy=y; }
        Rectangle rect(){ return new Rectangle(x,y,w,h); }
    }
    ArrayList<Spike> spikes = new ArrayList<>();

    // ── Holes (appear out of nowhere in platforms) ───────────
    static class AppearingHole {
        int platformIndex; // which platform gets the hole
        int holeX, holeW;  // position within platform
        int revealDelay;   // ticks before hole appears
        boolean active = false;
        boolean triggered = false;
        int warningFlash = 0;
        AppearingHole(int pi, int hx, int hw, int delay){
            platformIndex=pi; holeX=hx; holeW=hw; revealDelay=delay;
        }
    }
    ArrayList<AppearingHole> holes = new ArrayList<>();
    int holePlayerTick = 0; // counts while player is on a holed platform

    // ── Cannons ──────────────────────────────────────────────
    static class Cannon {
        int x, y;
        boolean facingRight;
        int fireTimer, fireRate;
        Cannon(int x, int y, boolean fr, int rate){
            this.x=x; this.y=y; this.facingRight=fr; this.fireRate=rate; this.fireTimer=rate/2;
        }
    }
    static class Cannonball {
        float x, y, vx;
        boolean alive = true;
        Cannonball(float x, float y, float vx){ this.x=x; this.y=y; this.vx=vx; }
        Rectangle rect(){ return new Rectangle((int)x-6,(int)y-6,12,12); }
    }
    ArrayList<Cannon> cannons = new ArrayList<>();
    ArrayList<Cannonball> cannonballs = new ArrayList<>();

    // ── Goal ─────────────────────────────────────────────────
    int goalX, goalY;
    static final int GOAL_W = 40, GOAL_H = 60;
    int levelW;

    // ── Hints ────────────────────────────────────────────────
    int levelTimer = 0;         // ticks since level started
    int hintIndex  = 0;
    boolean hintVisible = false;
    int hintFadeTimer = 0;
    static final int HINT_DELAY = 60 * 20; // show hint after 20 seconds of struggling

    // per-level hints (5 hints per level)
    static final String[][] LEVEL_HINTS = {
        // Level 1
        { "Hint 1: Look before you leap — gaps aren't always deadly.",
          "Hint 2: Purple blinking platforms are fake! They'll fall!",
          "Hint 3: Cyan platforms shoot you upward — use them!",
          "Hint 4: Cannons fire in a rhythm. Wait for the gap.",
          "Hint 5: The EXIT portal is at the far right. Almost there!" },
        // Level 2
        { "Hint 1: Moving platforms have a steady rhythm — time your jump.",
          "Hint 2: Fake platforms blink. Real ones are solid color.",
          "Hint 3: Spikes at platform edges — don't land on the very end!",
          "Hint 4: Bouncy pads launch you high. Aim for the upper ledge.",
          "Hint 5: Stay patient. Rushing causes deaths here." },
        // Level 3
        { "Hint 1: Some platforms SHIFT sideways when you land. Jump fast!",
          "Hint 2: Invisible platforms exist — look for faint sparkles.",
          "Hint 3: Chasing spikes speed up as you slow down. Keep moving!",
          "Hint 4: Holes appear in platforms after a short warning flash.",
          "Hint 5: The top path is safer but longer. Your call!" },
        // Level 4
        { "Hint 1: Every platform gap can be jumped — none require perfect timing.",
          "Hint 2: Yellow warning flicker = hole about to appear. Move!",
          "Hint 3: The chasing spike resets if you fall back to a safe zone.",
          "Hint 4: Bouncy pads are your friends — they skip dangerous sections.",
          "Hint 5: The final stretch has a wide safe platform. Sprint for it!" },
        // Level 5
        { "Hint 1: Breathe. Every troll has a solution.",
          "Hint 2: Invisible platforms are scattered throughout — probe edges.",
          "Hint 3: Moving platforms loop — just wait for them to come back.",
          "Hint 4: The upper path avoids most ground hazards.",
          "Hint 5: You're almost there. The portal is visible from the last island!" }
    };

    // ── Particles ─────────────────────────────────────────────
    static class Particle {
        float x, y, vx, vy;
        int life, maxLife;
        Color color;
        Particle(float x,float y,float vx,float vy,int life,Color c){
            this.x=x;this.y=y;this.vx=vx;this.vy=vy;this.life=life;this.maxLife=life;this.color=c;
        }
    }
    ArrayList<Particle> particles = new ArrayList<>();

    // ── Death messages ────────────────────────────────────────
    static final String[][] DEATH_MSGS = {
        { "Walked into a spike.", "Happens to everyone.", "OUCH" },
        { "Got yeeted by a cannonball.", "Didn't see that coming.", "BOOM" },
        { "Fell into a fake floor.", "It was blinking though...", "WHOOPS" },
        { "The platform had other plans.", "It moved. Classic.", "TROLLED" },
        { "Fell into a pit.", "Happens to the best.", "GRAVITY" },
        { "Got bonked mid-air.", "Physics said no.", "NOPE" },
        { "Landed on a hole.", "Floor went missing.", "YIKES" },
        { "The moving platform left.", "It'll come back, promise.", "TIMING" },
        { "Invisible platform was not there.", "Or was it?", "SNEAKY" },
        { "Chasing spike caught up.", "They're faster than they look.", "RUN" },
        { "Almost made it!", "Key word: almost.", "SO CLOSE" },
        { "The hole appeared right under you.", "Yellow means warning!", "WARNED" },
    };
    String[] currentDeathMsg = DEATH_MSGS[0];

    // ── Misc ──────────────────────────────────────────────────
    Timer timer;
    Random rng = new Random();
    int tick = 0;
    int menuSel = 0;
    int winTimer = 0;
    int blinkTick = 0;

    public platform() {
        setPreferredSize(new Dimension(W, H));
        setBackground(Color.BLACK);
        setFocusable(true);
        addKeyListener(this);
        timer = new Timer(16, this);
        timer.start();
    }

    // ══════════════════════════════════════════════════════════
    //  LEVEL BUILDING
    // ══════════════════════════════════════════════════════════

    void startLevel(int lvl) {
        platforms.clear(); spikes.clear(); cannons.clear();
        cannonballs.clear(); particles.clear(); holes.clear();
        px=60; py=400; pvx=0; pvy=0; camX=0;
        onGround=false; jumpConsumed=false; wasOnGround=false;
        coyoteTimer=0; jumpBufferTimer=0;
        winTimer=0; levelTimer=0; hintIndex=0;
        hintVisible=false; hintFadeTimer=0;

        switch(lvl) {
            case 0 -> buildLevel1();
            case 1 -> buildLevel2();
            case 2 -> buildLevel3();
            case 3 -> buildLevel4();
            case 4 -> buildLevel5();
        }
        state = State.PLAYING;
    }

    // ── LEVEL 1: "Welcome!" — easy, introduces mechanics ─────
    void buildLevel1() {
        levelW = 2800;
        // Wide comfortable ground sections
        addPlatform(0,   H-50, 350, 50);
        addPlatform(420, H-50, 280, 50);  // small gap
        addPlatform(760, H-50, 200, 50);
        addPlatform(1020,H-50, 300, 50);
        addPlatform(1400,H-50, 200, 50);
        addPlatform(1700,H-50, 200, 50);
        addPlatform(1980,H-50, 250, 50);
        addPlatform(2300,H-50, 500, 50);  // wide safe end

        // Floaters (generous width)
        addPlatform(430, 360, 130, 15);
        Platform fake1 = addPlatform(640, 330, 110, 15); fake1.fake=true;
        addPlatform(850, 300, 120, 15);

        // Bouncy platform — fun introduction
        Platform b1 = addPlatform(1060, 340, 100, 15); b1.bouncy=true;
        addPlatform(1200, 200, 120, 15); // landing after bounce

        // Moving platform — slow, easy
        Platform m1 = addPlatform(1440, 330, 110, 15);
        m1.moving=true; m1.mx=1440; m1.mrange=100; m1.mspeed=1.2f;

        // Invisible platform (with sparkle hint)
        Platform inv1 = addPlatform(1760, 360, 100, 15); inv1.invisible=true;

        // Troll: platform shifts when you land
        Platform shift1 = addPlatform(2010, 360, 120, 15);
        shift1.shiftOnStep=true; shift1.shiftDist=80;

        // Gentle spikes
        addSpikes(425, H-65, 2, 15);
        addSpikes(1025,H-65, 2, 15);
        addSpikes(1705,H-65, 2, 15);

        // One slow cannon
        cannons.add(new Cannon(1100, H-90, false, 140));

        // Appearing hole in a wide platform
        int pIdx = findPlatformAt(1400, H-50);
        if(pIdx >= 0) addHole(pIdx, 60, 30, 180); // appears after 3 sec

        goalX = 2550; goalY = H-110;
    }

    // ── LEVEL 2: "Getting There" ──────────────────────────────
    void buildLevel2() {
        levelW = 3200;
        addPlatform(0,   H-50, 280, 50);
        addPlatform(340, H-50, 220, 50);
        addPlatform(640, H-50, 180, 50);
        addPlatform(900, H-50, 200, 50);
        addPlatform(1180,H-50, 200, 50);
        addPlatform(1470,H-50, 180, 50);
        addPlatform(1740,H-50, 200, 50);
        addPlatform(2020,H-50, 180, 50);
        addPlatform(2300,H-50, 200, 50);
        addPlatform(2600,H-50, 600, 50);

        // Floaters
        Platform m1 = addPlatform(350, 350, 110, 15);
        m1.moving=true; m1.mx=350; m1.mrange=120; m1.mspeed=1.5f;

        addPlatform(660, 300, 100, 15);
        Platform f1 = addPlatform(830, 320, 100, 15); f1.fake=true;

        Platform b1 = addPlatform(950, 330, 90, 15); b1.bouncy=true;
        addPlatform(1100, 200, 110, 15);

        Platform inv1 = addPlatform(1240, 340, 100, 15); inv1.invisible=true;

        Platform m2 = addPlatform(1500, 310, 90, 15);
        m2.moving=true; m2.mx=1500; m2.mrange=130; m2.mspeed=1.8f;

        Platform shift1 = addPlatform(1770, 340, 110, 15);
        shift1.shiftOnStep=true; shift1.shiftDist=100;

        addPlatform(1970, 280, 100, 15);
        Platform f2 = addPlatform(2140, 310, 90, 15); f2.fake=true;

        Platform b2 = addPlatform(2350, 330, 90, 15); b2.bouncy=true;
        addPlatform(2480, 200, 110, 15);

        // Chasing spike on ground (slow)
        addSpikeChasing(200, H-65, 15, 1.2f);

        addSpikes(345, H-65, 2, 15);
        addSpikes(645, H-65, 2, 15);
        addSpikes(905, H-65, 2, 15);
        addSpikes(1185,H-65, 2, 15);
        addSpikes(1745,H-65, 2, 15);
        addSpikes(2025,H-65, 2, 15);

        // Holes
        int p1 = findPlatformAt(900, H-50);
        if(p1>=0) addHole(p1, 70, 30, 200);
        int p2 = findPlatformAt(1740, H-50);
        if(p2>=0) addHole(p2, 50, 30, 180);

        cannons.add(new Cannon(640,  H-90, false, 120));
        cannons.add(new Cannon(1470, H-90, false, 100));
        cannons.add(new Cannon(2300, H-90, false, 110));

        goalX = 2800; goalY = H-110;
    }

    // ── LEVEL 3: "Troll Central" ──────────────────────────────
    void buildLevel3() {
        levelW = 3600;
        addPlatform(0,   H-50, 220, 50);
        addPlatform(290, H-50, 180, 50);
        addPlatform(560, H-50, 160, 50);
        addPlatform(820, H-50, 180, 50);
        addPlatform(1100,H-50, 160, 50);
        addPlatform(1360,H-50, 180, 50);
        addPlatform(1640,H-50, 160, 50);
        addPlatform(1900,H-50, 180, 50);
        addPlatform(2180,H-50, 160, 50);
        addPlatform(2460,H-50, 180, 50);
        addPlatform(2740,H-50, 160, 50);
        addPlatform(3020,H-50, 180, 50);
        addPlatform(3300,H-50, 300, 50);

        // Floaters
        Platform m1 = addPlatform(300, 360, 110, 15);
        m1.moving=true; m1.mx=300; m1.mrange=140; m1.mspeed=1.8f;

        Platform inv1 = addPlatform(500, 330, 110, 15); inv1.invisible=true;
        Platform f1   = addPlatform(660, 310, 100, 15); f1.fake=true;
        addPlatform(870, 290, 110, 15);

        Platform shift1 = addPlatform(1000, 340, 110, 15);
        shift1.shiftOnStep=true; shift1.shiftDist=120;

        Platform b1 = addPlatform(1150, 330, 90, 15); b1.bouncy=true;
        addPlatform(1300, 190, 120, 15);

        Platform m2 = addPlatform(1440, 310, 100, 15);
        m2.moving=true; m2.mx=1440; m2.mrange=150; m2.mspeed=2.2f;

        Platform inv2 = addPlatform(1680, 340, 100, 15); inv2.invisible=true;
        Platform f2   = addPlatform(1820, 310, 90, 15); f2.fake=true;
        addPlatform(1980, 280, 110, 15);

        Platform shift2 = addPlatform(2120, 340, 100, 15);
        shift2.shiftOnStep=true; shift2.shiftDist=-120; // shifts LEFT

        Platform b2 = addPlatform(2350, 320, 90, 15); b2.bouncy=true;
        addPlatform(2480, 180, 110, 15);

        Platform m3 = addPlatform(2640, 300, 100, 15);
        m3.moving=true; m3.mx=2640; m3.mrange=160; m3.mspeed=2.5f;

        addPlatform(2900, 260, 110, 15);

        // Chasing spike (medium speed)
        addSpikeChasing(300, H-65, 15, 1.8f);

        // Holes
        int p1 = findPlatformAt(820, H-50);   if(p1>=0) addHole(p1,60,35,160);
        int p2 = findPlatformAt(1640, H-50);  if(p2>=0) addHole(p2,50,35,160);
        int p3 = findPlatformAt(2460, H-50);  if(p3>=0) addHole(p3,70,35,160);

        addSpikes(295, H-65,2,15); addSpikes(565, H-65,2,15);
        addSpikes(825, H-65,2,15); addSpikes(1105,H-65,2,15);
        addSpikes(1645,H-65,2,15); addSpikes(1905,H-65,2,15);
        addSpikes(2745,H-65,2,15); addSpikes(3025,H-65,2,15);

        cannons.add(new Cannon(560,  H-90, false, 100));
        cannons.add(new Cannon(1100, H-90, false, 90));
        cannons.add(new Cannon(1900, H-90, false, 85));
        cannons.add(new Cannon(2740, H-90, true,  95));

        goalX = 3400; goalY = H-110;
    }

    // ── LEVEL 4: "Almost There" ───────────────────────────────
    void buildLevel4() {
        levelW = 4000;
        addPlatform(0,   H-50, 180, 50);
        addPlatform(260, H-50, 160, 50);
        addPlatform(520, H-50, 160, 50);
        addPlatform(790, H-50, 160, 50);
        addPlatform(1060,H-50, 160, 50);
        addPlatform(1330,H-50, 160, 50);
        addPlatform(1600,H-50, 160, 50);
        addPlatform(1870,H-50, 160, 50);
        addPlatform(2150,H-50, 160, 50);
        addPlatform(2430,H-50, 160, 50);
        addPlatform(2710,H-50, 160, 50);
        addPlatform(2990,H-50, 160, 50);
        addPlatform(3270,H-50, 160, 50);
        addPlatform(3550,H-50, 450, 50); // safe end zone

        // Floaters
        Platform m1 = addPlatform(280, 360, 100, 15);
        m1.moving=true; m1.mx=280; m1.mrange=130; m1.mspeed=2.0f;

        Platform inv1 = addPlatform(490, 340, 100, 15); inv1.invisible=true;
        Platform f1   = addPlatform(680, 310, 95, 15); f1.fake=true;
        addPlatform(870, 290, 100, 15);

        Platform b1 = addPlatform(1000, 330, 90, 15); b1.bouncy=true;
        addPlatform(1150, 190, 120, 15);

        Platform shift1 = addPlatform(1280, 350, 100, 15);
        shift1.shiftOnStep=true; shift1.shiftDist=130;

        Platform m2 = addPlatform(1480, 310, 100, 15);
        m2.moving=true; m2.mx=1480; m2.mrange=160; m2.mspeed=2.4f;

        Platform inv2 = addPlatform(1680, 350, 100, 15); inv2.invisible=true;
        Platform f2   = addPlatform(1850, 310, 90, 15); f2.fake=true;
        addPlatform(2040, 280, 100, 15);

        Platform b2 = addPlatform(2220, 330, 90, 15); b2.bouncy=true;
        addPlatform(2360, 170, 110, 15);

        Platform m3 = addPlatform(2530, 300, 95, 15);
        m3.moving=true; m3.mx=2530; m3.mrange=170; m3.mspeed=2.8f;

        Platform shift2 = addPlatform(2730, 350, 100, 15);
        shift2.shiftOnStep=true; shift2.shiftDist=-140;

        Platform inv3 = addPlatform(2900, 330, 100, 15); inv3.invisible=true;
        addPlatform(3060, 280, 110, 15);

        Platform b3 = addPlatform(3200, 330, 90, 15); b3.bouncy=true;
        addPlatform(3350, 160, 120, 15);

        // Chasing spikes (two of them)
        addSpikeChasing(150, H-65, 15, 1.5f);
        addSpikeChasing(2200, H-65, 15, 2.0f);

        // Holes
        int[] hi = {2,4,6,8,10};
        for(int i : hi) {
            if(i < platforms.size()) {
                Platform pf = platforms.get(i);
                if(pf.w >= 100) addHole(i, pf.w/3, 30, 150);
            }
        }

        addSpikes(265, H-65,2,15); addSpikes(525, H-65,2,15);
        addSpikes(795, H-65,2,15); addSpikes(1065,H-65,2,15);
        addSpikes(1335,H-65,2,15); addSpikes(1605,H-65,2,15);
        addSpikes(1875,H-65,2,15); addSpikes(2155,H-65,2,15);

        cannons.add(new Cannon(520,  H-90, false, 90));
        cannons.add(new Cannon(1060, H-90, false, 80));
        cannons.add(new Cannon(1600, H-90, false, 75));
        cannons.add(new Cannon(2150, H-90, true,  85));
        cannons.add(new Cannon(2710, H-90, false, 70));
        cannons.add(new Cannon(3270, H-90, true,  80));

        goalX = 3700; goalY = H-110;
    }

    // ── LEVEL 5: "The Grand Finale" ───────────────────────────
    void buildLevel5() {
        levelW = 4500;
        addPlatform(0, H-50, 150, 50);
        // Chain of platforms
        int[] gx = {200,420,640,860,1080,1300,1520,1740,1960,2180,2400,2620,2840,3060,3280,3500,3720,3940,4200};
        for(int i=0;i<gx.length;i++){
            addPlatform(gx[i], H-50, 160+rng.nextInt(40), 50);
        }
        addPlatform(4300, H-50, 200, 50);

        // Floating path (mix of all mechanics)
        for(int i=0;i<12;i++){
            int bx = 220 + i*320;
            int by = 180 + rng.nextInt(120);
            int t = i % 5;
            Platform fp;
            if(t==0){ fp = addPlatform(bx,by,100,15); fp.moving=true; fp.mx=bx; fp.mrange=100+i*8; fp.mspeed=1.5f+i*0.12f; }
            else if(t==1){ fp = addPlatform(bx,by,100,15); fp.fake=true; }
            else if(t==2){ fp = addPlatform(bx,by,100,15); fp.invisible=true; }
            else if(t==3){ fp = addPlatform(bx,by,100,15); fp.bouncy=true; }
            else { fp = addPlatform(bx,by,100,15); fp.shiftOnStep=true; fp.shiftDist=(rng.nextBoolean()?1:-1)*(80+i*10); }
        }

        // Multiple chasing spikes
        addSpikeChasing(100,  H-65, 15, 1.3f);
        addSpikeChasing(2000, H-65, 15, 1.7f);
        addSpikeChasing(3500, H-65, 15, 2.0f);

        // Holes on several ground platforms
        for(int i=1;i<gx.length;i+=3){
            int pIdx = findPlatformAt(gx[i], H-50);
            if(pIdx>=0) addHole(pIdx, 40, 35, 120+i*10);
        }

        // Spikes on every gap
        for(int i=0;i<gx.length-1;i++) addSpikes(gx[i]+160, H-65, 2, 15);

        // Cannons
        for(int i=0;i<8;i++)
            cannons.add(new Cannon(400+i*500, H-90, i%2==0, 60+i*5));

        goalX = 4320; goalY = H-110;
    }

    // ── Helpers ───────────────────────────────────────────────
    Platform addPlatform(int x, int y, int w, int h) {
        Platform p = new Platform(x, y, w, h);
        platforms.add(p); return p;
    }
    void addSpikes(int x, int y, int count, int size) {
        for(int i=0;i<count;i++) spikes.add(new Spike(x+i*size, y, size, size));
    }
    Spike addSpikeChasing(int x, int y, int size, float speed) {
        Spike s = new Spike(x, y, size, size);
        s.chasing=true; s.cx=x; s.cy=y; s.cspeed=speed;
        spikes.add(s); return s;
    }
    void addHole(int pIdx, int hx, int hw, int delay) {
        holes.add(new AppearingHole(pIdx, hx, hw, delay));
    }
    int findPlatformAt(int tx, int ty) {
        for(int i=0;i<platforms.size();i++){
            Platform p = platforms.get(i);
            if(p.x<=tx && p.x+p.w>=tx && p.y==ty) return i;
        }
        return -1;
    }

    // ══════════════════════════════════════════════════════════
    //  GAME LOOP
    // ══════════════════════════════════════════════════════════
    @Override
    public void actionPerformed(ActionEvent e) {
        tick++;
        dotBobTick++;
        blinkTick++;
        if(state == State.PLAYING) update();
        if(state == State.WIN_LEVEL) {
            winTimer++;
            if(winTimer > 100) {
                currentLevel++;
                if(currentLevel >= 5) state = State.WIN_GAME;
                else startLevel(currentLevel);
            }
        }
        repaint();
    }

    void update() {
        levelTimer++;

        // ── Hint System ──────────────────────────────────────
        if(levelTimer % HINT_DELAY == 0 && hintIndex < 5) {
            hintVisible = true;
            hintFadeTimer = 300; // show for 5 seconds
        }
        if(hintVisible) {
            hintFadeTimer--;
            if(hintFadeTimer <= 0) {
                hintVisible = false;
                hintIndex = Math.min(hintIndex+1, 4);
            }
        }

        // ── Jump buffer ──────────────────────────────────────
        if(jumpDown) jumpBufferTimer = JUMP_BUFFER;
        else if(jumpBufferTimer > 0) jumpBufferTimer--;

        // ── Horizontal movement (smooth acceleration) ────────
        float targetVX = 0;
        if(leftDown)       { targetVX = -MOVE_SPD; facingRight=false; }
        else if(rightDown) { targetVX =  MOVE_SPD; facingRight=true; }

        if(onGround) {
            pvx += (targetVX - pvx) * ACCEL;
            if(Math.abs(targetVX) < 0.1f) pvx *= FRICTION;
        } else {
            pvx += (targetVX - pvx) * (ACCEL * 0.6f);
            pvx *= AIR_FRICTION;
        }
        if(Math.abs(pvx) < 0.08f) pvx = 0;

        // ── Coyote time ──────────────────────────────────────
        if(onGround) coyoteTimer = COYOTE_TIME;
        else if(coyoteTimer > 0) coyoteTimer--;

        wasOnGround = onGround;

        // ── Jump (with buffer + coyote) ───────────────────────
        boolean canJump = coyoteTimer > 0 && !jumpConsumed;
        if(jumpBufferTimer > 0 && canJump) {
            pvy = JUMP_VEL;
            jumpConsumed = true;
            jumpBufferTimer = 0;
            coyoteTimer = 0;
            spawnDust();
        }
        if(!jumpDown) jumpConsumed = false;

        // Variable jump height: release early = lower jump
        if(!jumpDown && pvy < -6f) pvy = Math.max(pvy + 1.2f, -6f);

        pvy = Math.min(pvy + GRAVITY, MAX_FALL);

        // ── Move X first, then Y ─────────────────────────────
        px += pvx;
        if(px < 0) px = 0;
        if(px > levelW - playerW) px = levelW - playerW;

        py += pvy;

        // ── Update chasing spikes ─────────────────────────────
        for(Spike s : spikes) {
            if(!s.chasing) continue;
            // Only chase if player is within horizontal range
            float dist = px - s.cx;
            if(Math.abs(dist) < 500) {
                float dir = dist > 0 ? 1 : -1;
                // Slow down when far, speed up when close
                float proximity = 1f - Math.min(Math.abs(dist)/400f, 0.8f);
                s.cx += dir * s.cspeed * (0.4f + proximity * 1.0f);
            }
            s.x = (int)s.cx;
        }

        // ── Update moving platforms ───────────────────────────
        for(Platform p : platforms) {
            if(p.moving) {
                p.x += p.mspeed * p.mdir;
                if(p.x > p.mx + p.mrange) p.mdir = -1;
                if(p.x < p.mx)            p.mdir =  1;
            }
            if(p.fake && p.fakeTriggered) {
                p.fakeTimer++;
                if(p.fakeTimer > 25) p.y += 5;
            }
            if(p.shiftOnStep && p.shiftTriggered) {
                p.shiftTimer++;
                if(p.shiftTimer <= 20) {
                    p.x += p.shiftDist / 20; // shift over 20 frames
                }
            }
            if(p.invisible && p.revealTimer_active) {
                p.revealFlash--;
                if(p.revealFlash <= 0) p.revealTimer_active = false;
            }
        }

        // ── Update appearing holes ────────────────────────────
        for(AppearingHole h : holes) {
            if(!h.triggered) {
                h.revealDelay--;
                if(h.revealDelay <= 60) h.warningFlash++; // warning flash period
                if(h.revealDelay <= 0) { h.active=true; h.triggered=true; }
            }
        }

        // ── Platform collision ────────────────────────────────
        onGround = false;
        Rectangle pr = new Rectangle((int)px, (int)py, playerW, playerH);

        for(int pIdx=0; pIdx<platforms.size(); pIdx++) {
            Platform p = platforms.get(pIdx);
            if(p.fake && p.fakeTimer > 50) continue;

            // Check if this platform has an active hole under the player
            boolean inHole = false;
            for(AppearingHole h : holes) {
                if(h.platformIndex == pIdx && h.active) {
                    int holeLeft  = p.x + h.holeX;
                    int holeRight = holeLeft + h.holeW;
                    float playerCX = px + playerW/2f;
                    if(playerCX > holeLeft && playerCX < holeRight) { inHole=true; break; }
                }
            }
            if(inHole) continue;

            Rectangle pl = p.rect();
            if(!pr.intersects(pl)) continue;

            // Land on top
            if(pvy >= 0 && py + playerH - pvy <= pl.y + 8) {
                py = pl.y - playerH;
                if(p.bouncy) {
                    pvy = JUMP_VEL * 1.5f;
                    spawnBounceParticles((int)px, (int)py);
                } else {
                    pvy = 0;
                    onGround = true;
                    if(p.fake && !p.fakeTriggered) p.fakeTriggered = true;
                    if(p.shiftOnStep && !p.shiftTriggered) p.shiftTriggered = true;
                    if(p.invisible) { p.revealTimer_active=true; p.revealFlash=45; }
                }
            }
            // Hit ceiling
            else if(pvy < 0 && py - pvy >= pl.y + pl.height - 5) {
                py = pl.y + pl.height; pvy = 1;
            }
            // Wall collisions
            else if(pvx > 0) { px = pl.x - playerW; pvx=0; }
            else if(pvx < 0) { px = pl.x + pl.width; pvx=0; }
        }

        // ── Fall death ────────────────────────────────────────
        if(py > H + 60) { killPlayer("Fell into a pit.", "Didn't jump. Incredible.", "GRAVITY"); return; }

        // ── Spike collision ───────────────────────────────────
        pr = new Rectangle((int)px+3, (int)py+2, playerW-6, playerH-2);
        for(Spike s : spikes) {
            if(pr.intersects(s.rect())) {
                if(s.chasing)
                    killPlayer("Chasing spike caught up.", "They're faster than they look.", "RUN");
                else
                    killPlayer("Walked into a spike.", "Happens to everyone.", "OUCH");
                return;
            }
        }

        // ── Cannon update ─────────────────────────────────────
        for(Cannon c : cannons) {
            c.fireTimer++;
            if(c.fireTimer >= c.fireRate) {
                c.fireTimer = 0;
                float vel = c.facingRight ? 5f : -5f;
                cannonballs.add(new Cannonball(c.x+(c.facingRight?30:-10), c.y+10, vel));
            }
        }
        Iterator<Cannonball> cit = cannonballs.iterator();
        while(cit.hasNext()) {
            Cannonball cb = cit.next();
            cb.x += cb.vx;
            if(cb.x < -50 || cb.x > levelW+50) { cit.remove(); continue; }
            if(pr.intersects(cb.rect())) {
                killPlayer("Got yeeted by a cannonball.", "Didn't see that coming.", "BOOM"); return;
            }
        }

        // ── Camera (smooth) ───────────────────────────────────
        float targetCam = px - W/2f + playerW/2f;
        camX += (targetCam - camX) * 0.10f;
        camX = Math.max(0, Math.min(camX, levelW - W));

        // ── Goal ──────────────────────────────────────────────
        Rectangle goalRect = new Rectangle(goalX, goalY, GOAL_W, GOAL_H);
        if(new Rectangle((int)px,(int)py,playerW,playerH).intersects(goalRect)) {
            totalScore += (5 - currentLevel) * 300 + 500;
            spawnWinBurst();
            state = State.WIN_LEVEL;
        }

        // ── Particles ─────────────────────────────────────────
        Iterator<Particle> it = particles.iterator();
        while(it.hasNext()) {
            Particle p = it.next();
            p.x += p.vx; p.y += p.vy; p.vy += 0.15f; p.life--;
            if(p.life <= 0) it.remove();
        }
    }

    void killPlayer(String l1, String l2, String tag) {
        if(state == State.DEAD) return;
        deaths++;
        currentDeathMsg = (rng.nextFloat() < 0.5f)
            ? new String[]{l1, l2, tag}
            : DEATH_MSGS[rng.nextInt(DEATH_MSGS.length)];
        spawnDeathBurst();
        state = State.DEAD;
    }

    void spawnDeathBurst() {
        for(int i=0;i<28;i++){
            float a=(float)(rng.nextDouble()*Math.PI*2);
            float s=1+rng.nextFloat()*5;
            particles.add(new Particle(px+playerW/2f,py+playerH/2f,
                (float)Math.cos(a)*s,(float)Math.sin(a)*s,
                25+rng.nextInt(20), new Color(100+rng.nextInt(80),0,rng.nextInt(80))));
        }
    }
    void spawnWinBurst() {
        for(int i=0;i<40;i++){
            float a=(float)(rng.nextDouble()*Math.PI*2);
            float s=2+rng.nextFloat()*5;
            particles.add(new Particle(goalX+GOAL_W/2f,goalY,
                (float)Math.cos(a)*s,(float)Math.sin(a)*s,
                30+rng.nextInt(20), new Color(rng.nextInt(255),rng.nextInt(255),rng.nextInt(100))));
        }
    }
    void spawnDust() {
        for(int i=0;i<5;i++)
            particles.add(new Particle(px+playerW/2f,py+playerH,
                (rng.nextFloat()-0.5f)*3,-rng.nextFloat()*2,
                12, new Color(200,200,200,180)));
    }
    void spawnBounceParticles(int x, int y) {
        for(int i=0;i<10;i++){
            float a=(float)(rng.nextDouble()*Math.PI);
            particles.add(new Particle(x+playerW/2f,y+playerH,
                (float)Math.cos(a)*4,(float)Math.sin(a)*-3,
                15, new Color(100,200,255)));
        }
    }

    // ══════════════════════════════════════════════════════════
    //  RENDERING
    // ══════════════════════════════════════════════════════════
    @Override
    protected void paintComponent(Graphics g0) {
        super.paintComponent(g0);
        Graphics2D g = (Graphics2D)g0;
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        switch(state) {
            case MENU      -> drawMenu(g);
            case PLAYING   -> drawGame(g);
            case DEAD      -> { drawGame(g); drawDeathScreen(g); }
            case WIN_LEVEL -> { drawGame(g); drawWinLevel(g); }
            case WIN_GAME  -> drawWinGame(g);
        }
    }

    // ── MENU ──────────────────────────────────────────────────
    void drawMenu(Graphics2D g) {
        float t = tick * 0.01f;
        Color c1 = new Color((int)(20+10*Math.sin(t)),(int)(5+5*Math.sin(t+1)),(int)(40+10*Math.sin(t+2)));
        Color c2 = new Color((int)(50+20*Math.sin(t+3)),(int)(10+10*Math.sin(t+4)),(int)(80+20*Math.sin(t+5)));
        g.setPaint(new GradientPaint(0,0,c1,W,H,c2));
        g.fillRect(0,0,W,H);

        g.setFont(new Font("Courier New", Font.BOLD, 54));
        String title = "i   LOST";
        drawShadowText(g, title, W/2-g.getFontMetrics().stringWidth(title)/2, 130,
            new Color(255,80,80), new Color(120,0,0));

        g.setFont(new Font("Courier New", Font.PLAIN, 15));
        g.setColor(new Color(200,200,220));
        String sub = "A troll platformer. Easier now, but still sneaky.";
        g.drawString(sub, W/2-g.getFontMetrics().stringWidth(sub)/2, 168);

        drawPlayerChar(g, W/2-7, 195, true, (tick/8)%4);

        String[] opts = {"▶  START GAME", "✕  QUIT"};
        for(int i=0;i<opts.length;i++) {
            boolean sel = (menuSel==i);
            g.setFont(new Font("Courier New", Font.BOLD, sel?26:22));
            Color col = sel ? new Color(255,200,80) : new Color(160,160,200);
            if(sel){ g.setColor(new Color(255,200,80,40)); g.fillRoundRect(W/2-130,303+i*60-28,260,36,8,8); }
            g.setColor(col);
            g.drawString(opts[i], W/2-g.getFontMetrics().stringWidth(opts[i])/2, 303+i*60);
        }

        g.setFont(new Font("Courier New", Font.PLAIN, 12));
        g.setColor(new Color(150,150,170));
        g.drawString("A/D: Move   SPACE: Jump   ↑↓: Select   ENTER: Confirm", W/2-230, H-30);

        // Troll mechanics legend
        g.setFont(new Font("Courier New", Font.PLAIN, 12));
        String[] legend = {
            "Purple blink = Fake  |  Cyan ↑↑ = Bouncy",
            "Faint shimmer = Invisible  |  Gold = Shifts away",
            "Red trails = Chasing spike  |  Yellow flash = Hole incoming"
        };
        for(int i=0;i<legend.length;i++){
            g.setColor(new Color(180,150,200));
            g.drawString(legend[i], W/2-g.getFontMetrics().stringWidth(legend[i])/2, 450+i*18);
        }
    }

    // ── GAME ──────────────────────────────────────────────────
    void drawGame(Graphics2D g) {
        int cx = (int)camX;

        // Sky
        g.setPaint(new GradientPaint(0,0,new Color(15,5,30),0,H,new Color(35,15,60)));
        g.fillRect(0,0,W,H);

        // Stars
        g.setColor(new Color(200,200,220,140));
        for(int i=0;i<80;i++){
            int sx = ((i*97+i*43) % levelW - cx/2 + levelW*2) % W;
            int sy = (i*53+i*11) % (H/2);
            g.fillRect(sx, sy, i%7==0?2:1, i%7==0?2:1);
        }

        // Platforms
        for(int pIdx=0; pIdx<platforms.size(); pIdx++) {
            Platform p = platforms.get(pIdx);
            int rx = p.x - cx;
            if(rx+p.w < -10 || rx > W+10) continue;

            // Gather hole masks for this platform
            ArrayList<int[]> holeMasks = new ArrayList<>();
            for(AppearingHole h : holes) {
                if(h.platformIndex == pIdx && h.active)
                    holeMasks.add(new int[]{h.holeX, h.holeW});
                else if(h.platformIndex == pIdx && !h.triggered && h.revealDelay <= 60) {
                    // Warning flash
                    if((blinkTick/5)%2==0) {
                        g.setColor(new Color(255,200,0,100));
                        g.fillRect(rx+h.holeX, p.y, h.holeW, p.h);
                    }
                }
            }

            if(p.invisible) {
                // Almost invisible — faint sparkle so player can find it
                if(p.revealTimer_active) {
                    // Revealed briefly
                    g.setColor(new Color(180,180,255,160));
                    drawPlatformSegmented(g, rx, p.y, p.w, p.h, holeMasks);
                } else {
                    // Subtle sparkle hint
                    float sparkle = (float)(0.3f + 0.15f*Math.sin(tick*0.08f+p.x*0.01f));
                    g.setColor(new Color(160,140,255,(int)(sparkle*80)));
                    for(int sx2=0; sx2<p.w; sx2+=12){
                        if((sx2/12 + blinkTick/8)%3==0)
                            g.fillOval(rx+sx2, p.y, 5, 5);
                    }
                }
                continue;
            }

            if(p.fake && p.fakeTriggered) {
                float crumble = Math.min(1f, p.fakeTimer/25f);
                g.setColor(new Color((int)(40+crumble*180),(int)(25*(1-crumble)),10));
                g.fillRect(rx, p.y, p.w, p.h);
                g.setColor(new Color(200,50,0));
                for(int i=0;i<3;i++) g.drawLine(rx+i*p.w/3,p.y,rx+i*p.w/3+5,p.y+p.h);
            } else if(p.fake) {
                boolean blink = (blinkTick/20)%2==0;
                g.setColor(blink ? new Color(80,50,120) : new Color(70,40,110));
                drawPlatformSegmented(g, rx, p.y, p.w, p.h, holeMasks);
                g.setColor(new Color(120,80,180));
                g.fillRect(rx, p.y, p.w, 6);
            } else if(p.bouncy) {
                float bounce = 0.5f+0.5f*(float)Math.sin(tick*0.2f);
                int b=(int)(50+80*bounce);
                g.setColor(new Color(b,(int)(150+50*bounce),(int)(200+50*bounce)));
                drawPlatformSegmented(g, rx, p.y, p.w, p.h, holeMasks);
                g.setColor(new Color(150,255,255));
                g.fillRect(rx,p.y,p.w,5);
                g.setColor(Color.WHITE);
                g.setFont(new Font("Courier New",Font.BOLD,9));
                if(p.w>40) g.drawString("↑↑↑",rx+p.w/2-10,p.y-2);
            } else if(p.shiftOnStep) {
                // Gold/orange color to hint it moves
                g.setColor(new Color(180,140,30));
                drawPlatformSegmented(g, rx, p.y, p.w, p.h, holeMasks);
                g.setColor(new Color(240,200,60));
                g.fillRect(rx,p.y,p.w,5);
                if(!p.shiftTriggered) {
                    g.setColor(new Color(255,230,80,120));
                    g.setFont(new Font("Courier New",Font.BOLD,8));
                    g.drawString("→",rx+p.w/2-3,p.y-2);
                }
            } else {
                // Normal
                Color topCol = new Color(60+currentLevel*8,100-currentLevel*4,60+currentLevel*4);
                g.setColor(new Color(50,35,15));
                drawPlatformSegmented(g, rx, p.y+7, p.w, p.h-7, holeMasks);
                g.setColor(topCol);
                drawPlatformSegmented(g, rx, p.y, p.w, 7, holeMasks);
            }
        }

        // Spikes
        for(Spike s : spikes) {
            int sx = s.x - cx;
            if(sx < -30 || sx > W+30) continue;
            if(s.chasing) {
                // Red glow for chasing spike
                g.setColor(new Color(255,50,0,60));
                g.fillOval(sx-8, s.y-8, s.w+16, s.h+16);
                // Trail
                g.setColor(new Color(255,50,0,40));
                g.fillRect(sx-15, s.y, 12, s.h);
                g.setColor(new Color(255,80,30));
            } else {
                g.setColor(new Color(200,200,220));
            }
            int[] xp={sx,sx+s.w/2,sx+s.w};
            int[] yp={s.y+s.h,s.y,s.y+s.h};
            g.fillPolygon(xp,yp,3);
            g.setColor(new Color(150,180,220));
            g.drawPolygon(xp,yp,3);
        }

        // Cannons
        for(Cannon c : cannons) {
            int ccx = c.x - cx;
            if(ccx<-60||ccx>W+60) continue;
            drawCannon(g, ccx, c.y, c.facingRight);
        }

        // Cannonballs
        for(Cannonball cb : cannonballs) {
            int bx = (int)(cb.x - cx);
            if(bx<-20||bx>W+20) continue;
            g.setColor(new Color(255,100,0,60)); g.fillOval(bx-10,(int)cb.y-10,20,20);
            g.setColor(new Color(50,50,50));     g.fillOval(bx-6,(int)cb.y-6,12,12);
            g.setColor(new Color(100,100,100));  g.fillOval(bx-4,(int)cb.y-4,8,8);
        }

        // Goal
        drawGoal(g, goalX-cx, goalY);

        // Particles
        for(Particle p : particles) {
            float alpha = (float)p.life/p.maxLife;
            g.setColor(new Color(p.color.getRed(),p.color.getGreen(),p.color.getBlue(),(int)(alpha*255)));
            g.fillOval((int)(p.x-cx)-3,(int)p.y-3,6,6);
        }

        // Player
        if(state != State.DEAD)
            drawPlayerChar(g,(int)(px-cx),(int)py,facingRight,onGround?(tick/6)%4:1);

        drawHUD(g);

        // Hint
        if(hintVisible && currentLevel < LEVEL_HINTS.length) {
            drawHint(g, LEVEL_HINTS[currentLevel][hintIndex], hintFadeTimer);
        }
    }

    // Draw platform with holes cut out
    void drawPlatformSegmented(Graphics2D g, int rx, int ry, int pw, int ph, ArrayList<int[]> holes2) {
        if(holes2.isEmpty()) { g.fillRect(rx, ry, pw, ph); return; }
        int drawn = 0;
        for(int[] h : holes2) {
            int hStart = h[0], hEnd = h[0]+h[1];
            g.fillRect(rx+drawn, ry, hStart-drawn, ph);
            drawn = hEnd;
        }
        g.fillRect(rx+drawn, ry, pw-drawn, ph);
    }

    void drawHint(Graphics2D g, String hint, int fadeTimer) {
        float alpha = Math.min(1f, fadeTimer/30f) * Math.min(1f, (fadeTimer-10)/20f);
        if(alpha <= 0) return;
        int a = (int)(alpha * 220);
        g.setColor(new Color(0,0,0,a));
        g.fillRoundRect(W/2-280, H-75, 560, 38, 10, 10);
        g.setColor(new Color(80,200,255,a));
        g.setStroke(new BasicStroke(1.5f));
        g.drawRoundRect(W/2-280, H-75, 560, 38, 10, 10);
        g.setStroke(new BasicStroke(1));
        g.setFont(new Font("Courier New", Font.BOLD, 13));
        g.setColor(new Color(200,240,255,a));
        g.drawString("💡 " + hint, W/2-g.getFontMetrics().stringWidth("💡 "+hint)/2, H-51);
    }

    void drawPlayerChar(Graphics2D g, int x, int y, boolean right, int frame) {
        int legSwing = (frame%2==0) ? 3 : -3;

        // Feet
        g.setColor(new Color(60,60,100));
        g.fillRect(x+2, y+playerH-8+legSwing/2, 5, 8);
        g.fillRect(x+7, y+playerH-8-legSwing/2, 5, 8);

        // Body (stem of i)
        g.setColor(new Color(220,220,255));
        g.fillRect(x+4, y+12, 6, playerH-20);
        g.setColor(new Color(160,160,200));
        g.fillRect(x+8, y+12, 2, playerH-20);

        // Serifs
        g.setColor(new Color(200,200,240));
        g.fillRect(x+1, y+playerH-12, 12, 3);
        g.fillRect(x+2, y+11, 10, 2);

        // Arms
        int armY = y+18;
        g.setColor(new Color(180,180,230));
        if(right){ g.fillRect(x-3,armY+legSwing/3,5,3); g.fillRect(x+playerW-2,armY-legSwing/3,5,3); }
        else      { g.fillRect(x-3,armY-legSwing/3,5,3); g.fillRect(x+playerW-2,armY+legSwing/3,5,3); }

        // Dot (bobbing)
        float bob = (float)Math.sin(dotBobTick*0.08f)*2.5f;
        int dotX = x+3, dotY = (int)(y-2+bob);

        g.setColor(new Color(200,150,255,80)); g.fillOval(dotX-4,dotY-4,16,16);
        g.setColor(new Color(230,200,255));    g.fillOval(dotX,dotY,8,8);
        g.setColor(Color.WHITE);               g.fillOval(dotX+2,dotY+1,3,3);

        // Eye
        g.setColor(new Color(50,30,80));
        if(right) g.fillOval(dotX+5,dotY+3,2,2);
        else      g.fillOval(dotX+1,dotY+3,2,2);

        // Scared face when moving fast
        if(Math.abs(pvx)>2 || Math.abs(pvy)>5) {
            g.setColor(new Color(50,30,80));
            g.drawArc(dotX+1,dotY+3,6,4,0,180);
        }
    }

    void drawCannon(Graphics2D g, int x, int y, boolean right) {
        g.setColor(new Color(60,40,20));     g.fillRect(x-5,y+20,40,20);
        g.setColor(new Color(40,25,10));     g.fillOval(x-2,y+28,16,16); g.fillOval(x+18,y+28,16,16);
        g.setColor(new Color(80,50,20));     g.fillOval(x+2,y+32,8,8);   g.fillOval(x+22,y+32,8,8);
        g.setColor(new Color(70,70,80));
        int bx = right ? x+20 : x-10;
        g.fillRoundRect(bx,y+8,30,14,6,6);
        if((tick/4)%3==0){ g.setColor(new Color(255,150,0,120)); int fx=right?x+48:x-14; g.fillOval(fx,y+8,14,14); }
    }

    void drawGoal(Graphics2D g, int x, int y) {
        float pulse = 0.5f+0.5f*(float)Math.sin(tick*0.07f);
        g.setColor(new Color(80,200,100,(int)(60+40*pulse)));  g.fillOval(x-20,y-20,GOAL_W+40,GOAL_H+40);
        g.setColor(new Color(0,180,80));  g.setStroke(new BasicStroke(4)); g.drawOval(x,y,GOAL_W,GOAL_H);
        g.setStroke(new BasicStroke(2));  g.setColor(new Color(100,255,150)); g.drawOval(x+5,y+5,GOAL_W-10,GOAL_H-10);
        g.setColor(new Color(50,180,100,(int)(100+60*pulse))); g.fillOval(x+8,y+8,GOAL_W-16,GOAL_H-16);
        g.setColor(Color.WHITE); g.setFont(new Font("Courier New",Font.BOLD,10)); g.drawString("EXIT",x+4,y+GOAL_H/2+4);
        g.setStroke(new BasicStroke(1));
    }

    void drawHUD(Graphics2D g) {
        g.setColor(new Color(0,0,0,150)); g.fillRoundRect(8,8,220,72,10,10);
        g.setFont(new Font("Courier New",Font.BOLD,13));
        g.setColor(new Color(100,220,255));
        g.drawString("LEVEL: "+(currentLevel+1)+"/5 — "+getLevelName(),16,27);
        g.setColor(new Color(255,220,80));
        g.drawString("SCORE: "+totalScore,16,45);
        g.setColor(new Color(255,100,100));
        g.drawString("DEATHS: "+deaths,16,63);

        g.setFont(new Font("Courier New",Font.PLAIN,11));
        g.setColor(new Color(255,255,255,90));
        g.drawString("A/D: Move   SPACE: Jump   R: Restart   ESC: Menu",W/2-175,H-10);
    }

    String getLevelName() {
        return switch(currentLevel){
            case 0->"Welcome!"; case 1->"Getting There"; case 2->"Troll Central";
            case 3->"Almost There"; case 4->"The Finale"; default->"???";
        };
    }

    // ── DEATH SCREEN ──────────────────────────────────────────
    void drawDeathScreen(Graphics2D g) {
        g.setColor(new Color(0,0,0,160)); g.fillRect(0,0,W,H);
        int pw=500,ph=220,panX=W/2-pw/2,panY=H/2-ph/2;
        g.setColor(new Color(15,5,25,230)); g.fillRoundRect(panX,panY,pw,ph,18,18);
        g.setColor(new Color(200,30,30)); g.setStroke(new BasicStroke(2));
        g.drawRoundRect(panX,panY,pw,ph,18,18); g.setStroke(new BasicStroke(1));

        g.setFont(new Font("Courier New",Font.BOLD,42));
        drawShadowText(g,currentDeathMsg[2],W/2-g.getFontMetrics().stringWidth(currentDeathMsg[2])/2,panY+55,
            new Color(255,60,60),new Color(100,0,0));

        g.setFont(new Font("Courier New",Font.BOLD,20));
        g.setColor(new Color(240,220,220));
        String msg=currentDeathMsg[0];
        g.drawString(msg,W/2-g.getFontMetrics().stringWidth(msg)/2,panY+100);

        g.setFont(new Font("Courier New",Font.ITALIC,15));
        g.setColor(new Color(180,160,180));
        String sub=currentDeathMsg[1];
        g.drawString(sub,W/2-g.getFontMetrics().stringWidth(sub)/2,panY+125);

        g.setFont(new Font("Courier New",Font.PLAIN,13));
        g.setColor(new Color(255,120,120));
        String dc="Total deaths: "+deaths+" — you got this!";
        g.drawString(dc,W/2-g.getFontMetrics().stringWidth(dc)/2,panY+155);

        if((tick/20)%2==0) {
            g.setFont(new Font("Courier New",Font.BOLD,15));
            g.setColor(new Color(255,200,80));
            String pr="R: Try Again   |   ESC: Menu";
            g.drawString(pr,W/2-g.getFontMetrics().stringWidth(pr)/2,panY+193);
        }
    }

    // ── WIN LEVEL ─────────────────────────────────────────────
    void drawWinLevel(Graphics2D g) {
        g.setColor(new Color(0,0,0,120)); g.fillRect(0,0,W,H);
        int pw=440,ph=160,panX=W/2-pw/2,panY=H/2-ph/2-30;
        g.setColor(new Color(5,25,15,220)); g.fillRoundRect(panX,panY,pw,ph,15,15);
        g.setColor(new Color(0,200,80)); g.setStroke(new BasicStroke(2));
        g.drawRoundRect(panX,panY,pw,ph,15,15); g.setStroke(new BasicStroke(1));

        g.setFont(new Font("Courier New",Font.BOLD,34));
        String msg = (currentLevel>=4) ? "YOU DID IT!!!" : "LEVEL CLEAR!";
        drawShadowText(g,msg,W/2-g.getFontMetrics().stringWidth(msg)/2,panY+50,
            new Color(80,255,130),new Color(0,80,40));

        g.setFont(new Font("Courier New",Font.PLAIN,15));
        g.setColor(new Color(180,240,200));
        String sub=(currentLevel<4)?"Next level incoming... (stay sharp!)":"You beat it. Genuinely impressive.";
        g.drawString(sub,W/2-g.getFontMetrics().stringWidth(sub)/2,panY+83);

        g.setColor(new Color(0,80,40));
        g.fillRoundRect(panX+20,panY+103,pw-40,18,6,6);
        float prog=(float)winTimer/100;
        g.setColor(new Color(0,200,80));
        g.fillRoundRect(panX+20,panY+103,(int)((pw-40)*prog),18,6,6);
        g.setFont(new Font("Courier New",Font.PLAIN,11));
        g.setColor(new Color(255,255,255,150));
        g.drawString("Loading next level...",panX+20,panY+116);
    }

    // ── WIN GAME ──────────────────────────────────────────────
    void drawWinGame(Graphics2D g) {
        float t=tick*0.015f;
        g.setPaint(new GradientPaint(0,0,
            new Color((int)(10+10*Math.sin(t)),30,(int)(10+10*Math.sin(t+2))),
            W,H,new Color(5,(int)(30+15*Math.sin(t+1)),10)));
        g.fillRect(0,0,W,H);

        for(Particle p : particles) {
            float alpha=(float)p.life/p.maxLife;
            g.setColor(new Color(p.color.getRed(),p.color.getGreen(),p.color.getBlue(),(int)(alpha*255)));
            g.fillRect((int)(p.x-camX/4)-3,(int)p.y-3,6,6);
        }

        g.setFont(new Font("Courier New",Font.BOLD,52));
        drawShadowText(g,"YOU ESCAPED!",W/2-g.getFontMetrics().stringWidth("YOU ESCAPED!")/2,130,
            new Color(100,255,140),new Color(0,100,50));

        g.setFont(new Font("Courier New",Font.ITALIC,18));
        String[] lines={
            "Against all odds. Against all trolls.",
            "You, a tiny letter 'i', survived 5 levels.",
            "The dot on your head wobbled with pride.",
            "",
            "Final Score: "+totalScore,
            "Total Deaths: "+deaths+"  ("+getRank()+")"
        };
        for(int i=0;i<lines.length;i++){
            g.setColor(lines[i].startsWith("Final")||lines[i].startsWith("Total")
                ?new Color(255,230,80):new Color(180,240,195));
            g.drawString(lines[i],W/2-g.getFontMetrics().stringWidth(lines[i])/2,195+i*32);
        }

        if((tick/25)%2==0){
            g.setFont(new Font("Courier New",Font.BOLD,16));
            g.setColor(new Color(255,200,80));
            String pr="ESC: Main Menu";
            g.drawString(pr,W/2-g.getFontMetrics().stringWidth(pr)/2,H-30);
        }

        if(tick%3==0 && particles.size()<200)
            particles.add(new Particle(rng.nextInt(W),-10,
                (rng.nextFloat()-0.5f)*2,2+rng.nextFloat()*3,
                120+rng.nextInt(60),new Color(rng.nextInt(255),rng.nextInt(255),rng.nextInt(255))));

        Iterator<Particle> it=particles.iterator();
        while(it.hasNext()){ Particle p=it.next(); p.x+=p.vx; p.y+=p.vy; p.life--; if(p.life<=0) it.remove(); }
    }

    String getRank() {
        if(deaths==0)      return "Impossible. Are you human?";
        else if(deaths<5)  return "Suspiciously good";
        else if(deaths<15) return "Genuinely impressive";
        else if(deaths<30) return "Solid run";
        else if(deaths<60) return "Getting there";
        else if(deaths<100)return "Persistent";
        else               return "Never give up!";
    }

    void drawShadowText(Graphics2D g, String text, int x, int y, Color main, Color shadow) {
        g.setColor(shadow); g.drawString(text,x+3,y+3);
        g.setColor(main);   g.drawString(text,x,y);
    }

    // ══════════════════════════════════════════════════════════
    //  KEY HANDLING
    // ══════════════════════════════════════════════════════════
    @Override
    public void keyPressed(KeyEvent e) {
        int k = e.getKeyCode();

        if(state==State.MENU){
            if(k==KeyEvent.VK_UP  ||k==KeyEvent.VK_W) menuSel=Math.max(0,menuSel-1);
            if(k==KeyEvent.VK_DOWN||k==KeyEvent.VK_S) menuSel=Math.min(1,menuSel+1);
            if(k==KeyEvent.VK_ENTER||k==KeyEvent.VK_SPACE){
                if(menuSel==0){ currentLevel=0; totalScore=0; deaths=0; startLevel(0); }
                else System.exit(0);
            }
            return;
        }
        if(state==State.WIN_GAME){
            if(k==KeyEvent.VK_ESCAPE){ state=State.MENU; particles.clear(); }
            return;
        }

        if(k==KeyEvent.VK_A||k==KeyEvent.VK_LEFT)  leftDown=true;
        if(k==KeyEvent.VK_D||k==KeyEvent.VK_RIGHT) rightDown=true;
        if(k==KeyEvent.VK_SPACE||k==KeyEvent.VK_UP||k==KeyEvent.VK_W) jumpDown=true;
        if(k==KeyEvent.VK_R) { if(state==State.DEAD||state==State.PLAYING) startLevel(currentLevel); }
        if(k==KeyEvent.VK_ESCAPE){ state=State.MENU; particles.clear(); leftDown=rightDown=jumpDown=false; }
    }

    @Override
    public void keyReleased(KeyEvent e) {
        int k=e.getKeyCode();
        if(k==KeyEvent.VK_A||k==KeyEvent.VK_LEFT)  leftDown=false;
        if(k==KeyEvent.VK_D||k==KeyEvent.VK_RIGHT) rightDown=false;
        if(k==KeyEvent.VK_SPACE||k==KeyEvent.VK_UP||k==KeyEvent.VK_W) jumpDown=false;
    }

    @Override public void keyTyped(KeyEvent e) {}

    // ── Main ──────────────────────────────────────────────────
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            JFrame frame = new JFrame("i LOST — Troll Platformer");
            platform game = new platform();
            frame.add(game);
            frame.pack();
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            frame.setLocationRelativeTo(null);
            frame.setResizable(false);
            frame.setVisible(true);
            game.requestFocusInWindow();
        });
    }
}