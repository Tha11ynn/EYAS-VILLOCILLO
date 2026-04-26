import java.awt.*;
import java.awt.event.*;
import java.awt.geom.*;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.Random;
import javax.swing.*;

public class platform extends JPanel implements ActionListener, KeyListener {

    // ── Window ──────────────────────────────────────────────
    static final int W = 900, H = 550;

    // ── Physics ──────────────────────────────────────────────
    static final float GRAVITY      = 0.55f;
    static final float JUMP_VEL     = -14.5f;
    static final float MOVE_SPD     = 5.5f;
    static final float ACCEL        = 0.85f;
    static final float FRICTION     = 0.78f;
    static final float AIR_FRICTION = 0.92f;
    static final float MAX_FALL     = 14f;
    static final int   COYOTE_TIME  = 8;
    static final int   JUMP_BUFFER  = 8;

    // ── Game States ──────────────────────────────────────────
    enum State { MENU, LEVEL_SELECT, PLAYING, PAUSED, DYING, DEAD, WIN_LEVEL, WIN_GAME, TRANSITIONING }
    State state = State.MENU;

    // ── Wipe Transition ──────────────────────────────────────
    static final int WIPE_TICKS = 38;
    int   wipeTimer      = 0;
    State wipeTargetState = State.PLAYING;
    int   wipePendingLevel = 0;

    int currentLevel = 0;
    int totalScore   = 0;
    int deaths       = 0;
    int highestUnlockedLevel = 0;

    // ── Player ───────────────────────────────────────────────
    float px = 80, py = 300, pvx = 0, pvy = 0;
    boolean onGround    = false;
    boolean facingRight = true;
    int playerW = 14, playerH = 36;
    int dotBobTick = 0;
    int coyoteTimer   = 0;
    int jumpBufferTimer = 0;
    boolean wasOnGround = false;

    // ── Death Animation ──────────────────────────────────────
    static final int DEATH_ANIM_TICKS = 55;
    int deathAnimTimer = 0;
    float deathX, deathY;
    static class BodyPart {
        float x, y, vx, vy, rot, rotV;
        int type;
        int life;
        Color color;
        BodyPart(float x, float y, float vx, float vy, float rotV, int type, Color c) {
            this.x=x; this.y=y; this.vx=vx; this.vy=vy; this.rot=0; this.rotV=rotV;
            this.type=type; this.life=DEATH_ANIM_TICKS + 10; this.color=c;
        }
    }
    ArrayList<BodyPart> bodyParts = new ArrayList<>();
    int screenShakeTick = 0;
    float screenShakeX = 0, screenShakeY = 0;

    // ── Input ────────────────────────────────────────────────
    boolean leftDown, rightDown, jumpDown, jumpConsumed;

    // ── Camera ───────────────────────────────────────────────
    float camX = 0;

    // ── Menu Selection ───────────────────────────────────────
    int menuSel = 0;
    int levelSelectSel = 0;
    static final String[] MENU_OPTIONS = { "▶  START GAME", "⊞  LEVEL SELECT", "✕  QUIT" };
    static final String[] LEVEL_NAMES = { "Tutorial", "Getting There", "Troll Central", "Almost There", "The Finale" };

    // ── NEW: Menu animation fields ────────────────────────────
    float[] menuParticleX   = new float[60];
    float[] menuParticleY   = new float[60];
    float[] menuParticleVY  = new float[60];
    float[] menuParticleSz  = new float[60];
    int[]   menuParticleHue = new int[60];
    boolean menuParticlesInit = false;
    int     glitchTick  = 0;
    int     glitchTimer = 0;
    float   glitchOffX  = 0;
    int     tickerOffset = 0;

    // ── Pause Menu ───────────────────────────────────────────
    int pauseSel = 0;
    boolean pauseLevelSelect = false;
    int pauseLevelSel = 0;
    static final String[] PAUSE_OPTIONS = { "▶  RESUME", "↺  RESTART LEVEL", "⊞  LEVEL SELECT", "♪  VOLUME", "⌂  QUIT TO MENU" };
    int volume = 80;

    // ── Tutorial System ──────────────────────────────────────
    static class TutorialCard {
        int triggerX;
        String title;
        String[] lines;
        Color accentColor;
        boolean shown = false;
        int displayTimer = 0;
        static final int DISPLAY_TICKS = 260;
        TutorialCard(int tx, String title, Color accent, String... lines) {
            this.triggerX = tx; this.title = title;
            this.accentColor = accent; this.lines = lines;
        }
    }
    ArrayList<TutorialCard> tutCards = new ArrayList<>();
    TutorialCard activeTutCard = null;

    // ── Platform ─────────────────────────────────────────────
    static class Platform {
        int x, y, w, h;
        boolean fake;
        boolean bouncy;
        boolean moving;
        boolean invisible;
        float mx, my, mrange, mspeed, mdir = 1;
        int   fakeTimer = 0;
        boolean fakeTriggered = false;
        boolean revealTimer_active = false;
        int revealFlash = 0;
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
        boolean chasing;
        float cx, cy, cspeed;
        boolean cdir = true;
        Spike(int x, int y, int w, int h){ this.x=x; this.y=y; this.w=w; this.h=h;
            this.cx=x; this.cy=y; }
        Rectangle rect(){ return new Rectangle(x,y,w,h); }
    }
    ArrayList<Spike> spikes = new ArrayList<>();

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

    // ── Level Timer ──────────────────────────────────────────
    int levelTimer = 0;

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
        { null, null, null },
    };
    String[] currentDeathMsg = DEATH_MSGS[0];
    int lastDeathMsg = -1;

    // ── Misc ──────────────────────────────────────────────────
    Timer timer;
    Random rng = new Random();
    int tick = 0;
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

    void startLevel(int lvl) {
        platforms.clear(); spikes.clear(); cannons.clear();
        cannonballs.clear(); particles.clear(); bodyParts.clear();
        tutCards.clear(); activeTutCard = null;
        px=60; py=400; pvx=0; pvy=0; camX=0;
        onGround=false; jumpConsumed=false; wasOnGround=false;
        coyoteTimer=0; jumpBufferTimer=0;
        winTimer=0; levelTimer=0;
        deathAnimTimer=0; screenShakeTick=0;
        leftDown=false; rightDown=false; jumpDown=false; jumpConsumed=false;
        switch(lvl) {
            case 0 -> buildLevel1();
            case 1 -> buildLevel2();
            case 2 -> buildLevel3();
            case 3 -> buildLevel4();
            case 4 -> buildLevel5();
        }
        state = State.PLAYING;
    }

    void beginWipeToLevel(int lvl) {
        wipePendingLevel = lvl;
        wipeTargetState  = State.PLAYING;
        wipeTimer        = 0;
        platforms.clear(); spikes.clear(); cannons.clear();
        cannonballs.clear(); particles.clear(); bodyParts.clear();
        tutCards.clear(); activeTutCard = null;
        px=60; py=400; pvx=0; pvy=0; camX=0;
        onGround=false; jumpConsumed=false; wasOnGround=false;
        coyoteTimer=0; jumpBufferTimer=0;
        winTimer=0; levelTimer=0;
        deathAnimTimer=0; screenShakeTick=0;
        leftDown=false; rightDown=false; jumpDown=false; jumpConsumed=false;
        switch(lvl) {
            case 0 -> buildLevel1();
            case 1 -> buildLevel2();
            case 2 -> buildLevel3();
            case 3 -> buildLevel4();
            case 4 -> buildLevel5();
        }
        state = State.TRANSITIONING;
    }

    void buildLevel1() {
        levelW = 4100;
        addPlatform(0, H-50, 500, 50);
        addPlatform(560, H-50, 300, 50);
        addPlatform(920, H-50, 300, 50);
        addPlatform(1280, H-50, 320, 50);
        addSpikes(1420, H-65, 2, 15);
        addPlatform(1660, H-50, 300, 50);
        addPlatform(1660, 380, 110, 15);
        Platform fake1 = addPlatform(1830, 380, 110, 15);
        fake1.fake = true;
        addPlatform(2020, H-50, 300, 50);
        Platform b1 = addPlatform(2070, 370, 100, 15);
        b1.bouncy = true;
        addPlatform(2210, 220, 140, 15);
        addPlatform(2380, H-50, 280, 50);
        Platform m1 = addPlatform(2490, 360, 110, 15);
        m1.moving = true; m1.mx = 2490; m1.mrange = 100; m1.mspeed = 1.3f;
        addPlatform(2720, H-50, 200, 50);
        addPlatform(2980, H-50, 220, 50);
        Platform inv1 = addPlatform(3060, 360, 110, 15);
        inv1.invisible = true;
        addPlatform(3260, H-50, 240, 50);
        addPlatform(3560, H-50, 250, 50);
        cannons.add(new Cannon(3280, H-90, false, 150));
        addPlatform(3870, H-50, 300, 50);

        tutCards.add(new TutorialCard(20, "MOVEMENT", new Color(100, 200, 255),
            "A / D  or  ← →   to move",
            "SPACE / W / ↑  to jump",
            "ESC to pause   R to restart"));
        tutCards.add(new TutorialCard(460, "GAPS", new Color(255, 200, 80),
            "Some platforms don't connect.",
            "Jump across gaps — they're all",
            "reachable with a normal jump!"));
        tutCards.add(new TutorialCard(1220, "SPIKES  ⚠", new Color(255, 100, 100),
            "White triangles = instant death.",
            "Jump OVER them or go around.",
            "They're always avoidable!"));
        tutCards.add(new TutorialCard(1610, "FAKE PLATFORMS", new Color(180, 100, 255),
            "Sometimes platforms fall",
            "when you step on them!",
            "Make sure to react fast."));
        tutCards.add(new TutorialCard(1980, "BOUNCY PADS  ↑↑", new Color(80, 220, 255),
            "Cyan platforms with ↑↑ launch",
            "you super high. Use them to reach",
            "upper ledges — don't panic!"));
        tutCards.add(new TutorialCard(2360, "MOVING PLATFORMS", new Color(100, 255, 160),
            "Some platforms slide back and forth.",
            "Wait for them to come to you,",
            "then ride them to the next gap."));
        tutCards.add(new TutorialCard(2920, "INVISIBLE PLATFORMS", new Color(200, 170, 255),
            "Some platforms are near-invisible!",
            "Look for faint sparkles ✦",
            "Step carefully — they're solid."));
        tutCards.add(new TutorialCard(3200, "CANNONS  💥", new Color(255, 120, 60),
            "Cannons fire on a rhythm.",
            "Watch the timing — wait for",
            "the gap, then run through!"));

        goalX = 3950; goalY = H-110;
    }

    void buildLevel2() {
        levelW = 3200;
        addPlatform(0, H-50, 280, 50); addPlatform(340, H-50, 220, 50);
        addPlatform(640, H-50, 180, 50); addPlatform(900, H-50, 200, 50);
        addPlatform(1180, H-50, 200, 50); addPlatform(1470, H-50, 180, 50);
        addPlatform(1740, H-50, 200, 50); addPlatform(2020, H-50, 180, 50);
        addPlatform(2300, H-50, 200, 50); addPlatform(2600, H-50, 600, 50);
        Platform m1 = addPlatform(350, 350, 110, 15);
        m1.moving = true; m1.mx = 350; m1.mrange = 120; m1.mspeed = 1.5f;
        addPlatform(660, 300, 100, 15); addSpikes(680, 285, 2, 15);
        Platform f1 = addPlatform(830, 320, 100, 15); f1.fake = true;
        Platform b1 = addPlatform(950, 330, 90, 15); b1.bouncy = true;
        addPlatform(1100, 200, 110, 15);
        Platform inv1 = addPlatform(1240, 340, 100, 15); inv1.invisible = true;
        Platform m2 = addPlatform(1500, 310, 90, 15);
        m2.moving = true; m2.mx = 1500; m2.mrange = 130; m2.mspeed = 1.8f;
        Platform shift1 = addPlatform(1770, 340, 110, 15);
        shift1.shiftOnStep = true; shift1.shiftDist = 100;
        addPlatform(1970, 280, 100, 15);
        Platform f2 = addPlatform(2140, 310, 90, 15); f2.fake = true;
        Platform b2 = addPlatform(2350, 330, 90, 15); b2.bouncy = true;
        addPlatform(2480, 200, 110, 15);
        addSpikeChasing(200, H-65, 15, 1.2f);
        addSpikes(345, H-65, 2, 15); addSpikes(645, H-65, 2, 15);
        addSpikes(905, H-65, 2, 15); addSpikes(1185, H-65, 2, 15);
        addSpikes(1745, H-65, 2, 15); addSpikes(2025, H-65, 2, 15);
        cannons.add(new Cannon(640, H-90, false, 120));
        cannons.add(new Cannon(1470, H-90, false, 100));
        cannons.add(new Cannon(2300, H-90, false, 110));
        goalX = 2800; goalY = H-110;
    }

    void buildLevel3() {
        levelW = 3700;
        addPlatform(0, H-50, 220, 50); addPlatform(290, H-50, 180, 50);
        addPlatform(560, H-50, 160, 50); addPlatform(820, H-50, 180, 50);
        addPlatform(1100, H-50, 160, 50); addPlatform(1360, H-50, 180, 50);
        addPlatform(1640, H-50, 160, 50); addPlatform(1900, H-50, 180, 50);
        addPlatform(2180, H-50, 160, 50); addPlatform(2460, H-50, 180, 50);
        addPlatform(2740, H-50, 160, 50); addPlatform(3020, H-50, 180, 50);
        addPlatform(3300, H-50, 300, 50);
        Platform m1 = addPlatform(300, 360, 110, 15);
        m1.moving = true; m1.mx = 300; m1.mrange = 140; m1.mspeed = 1.8f;
        Platform inv1 = addPlatform(500, 330, 110, 15); inv1.invisible = true;
        Platform f1 = addPlatform(660, 310, 100, 15); f1.fake = true;
        addPlatform(870, 290, 110, 15); addSpikes(885, 275, 2, 15);
        Platform shift1 = addPlatform(1000, 340, 110, 15);
        shift1.shiftOnStep = true; shift1.shiftDist = 120;
        Platform b1 = addPlatform(1150, 330, 90, 15); b1.bouncy = true;
        addPlatform(1300, 190, 120, 15); addSpikes(1320, 175, 2, 15);
        Platform m2 = addPlatform(1440, 310, 100, 15);
        m2.moving = true; m2.mx = 1440; m2.mrange = 150; m2.mspeed = 2.2f;
        Platform inv2 = addPlatform(1680, 340, 100, 15); inv2.invisible = true;
        Platform f2 = addPlatform(1820, 310, 90, 15); f2.fake = true;
        addPlatform(1980, 280, 110, 15); addSpikes(1995, 265, 2, 15);
        Platform shift2 = addPlatform(2120, 340, 100, 15);
        shift2.shiftOnStep = true; shift2.shiftDist = -120;
        Platform b2 = addPlatform(2350, 320, 90, 15); b2.bouncy = true;
        addPlatform(2480, 180, 110, 15);
        Platform m3 = addPlatform(2640, 300, 100, 15);
        m3.moving = true; m3.mx = 2640; m3.mrange = 160; m3.mspeed = 2.5f;
        addPlatform(2900, 260, 110, 15); addSpikes(2915, 245, 2, 15);
        addSpikeChasing(300, H-65, 15, 1.8f);
        addSpikes(295, H-65, 2, 15); addSpikes(565, H-65, 2, 15);
        addSpikes(825, H-65, 2, 15); addSpikes(1105, H-65, 2, 15);
        addSpikes(1645, H-65, 2, 15); addSpikes(1905, H-65, 2, 15);
        addSpikes(2745, H-65, 2, 15); addSpikes(3025, H-65, 2, 15);
        cannons.add(new Cannon(560, H-90, false, 100));
        cannons.add(new Cannon(1100, H-90, false, 90));
        cannons.add(new Cannon(1900, H-90, false, 85));
        cannons.add(new Cannon(2740, H-90, true, 95));
        goalX = 3400; goalY = H-110;
    }

    void buildLevel4() {
        levelW = 4000;
        addPlatform(0, H-50, 180, 50); addPlatform(260, H-50, 160, 50);
        addPlatform(520, H-50, 160, 50); addPlatform(790, H-50, 160, 50);
        addPlatform(1060, H-50, 160, 50); addPlatform(1330, H-50, 160, 50);
        addPlatform(1600, H-50, 160, 50); addPlatform(1870, H-50, 160, 50);
        addPlatform(2150, H-50, 160, 50); addPlatform(2430, H-50, 160, 50);
        addPlatform(2710, H-50, 160, 50); addPlatform(2990, H-50, 160, 50);
        addPlatform(3270, H-50, 160, 50); addPlatform(3550, H-50, 450, 50);
        Platform m1 = addPlatform(280, 360, 100, 15);
        m1.moving = true; m1.mx = 280; m1.mrange = 130; m1.mspeed = 2.0f;
        Platform inv1 = addPlatform(490, 340, 100, 15); inv1.invisible = true;
        Platform f1 = addPlatform(680, 310, 95, 15); f1.fake = true;
        addPlatform(870, 290, 100, 15); addSpikes(882, 275, 2, 15);
        Platform b1 = addPlatform(1000, 330, 90, 15); b1.bouncy = true;
        addPlatform(1150, 190, 120, 15); addSpikes(1165, 175, 3, 15);
        Platform shift1 = addPlatform(1280, 350, 100, 15);
        shift1.shiftOnStep = true; shift1.shiftDist = 130;
        Platform m2 = addPlatform(1480, 310, 100, 15);
        m2.moving = true; m2.mx = 1480; m2.mrange = 160; m2.mspeed = 2.4f;
        Platform inv2 = addPlatform(1680, 350, 100, 15); inv2.invisible = true;
        Platform f2 = addPlatform(1850, 310, 90, 15); f2.fake = true;
        addPlatform(2040, 280, 100, 15); addSpikes(2055, 265, 2, 15);
        Platform b2 = addPlatform(2220, 330, 90, 15); b2.bouncy = true;
        addPlatform(2360, 170, 110, 15); addSpikes(2375, 155, 3, 15);
        Platform m3 = addPlatform(2530, 300, 95, 15);
        m3.moving = true; m3.mx = 2530; m3.mrange = 170; m3.mspeed = 2.8f;
        Platform shift2 = addPlatform(2730, 350, 100, 15);
        shift2.shiftOnStep = true; shift2.shiftDist = -140;
        Platform inv3 = addPlatform(2900, 330, 100, 15); inv3.invisible = true;
        addPlatform(3060, 280, 110, 15); addSpikes(3075, 265, 2, 15);
        Platform b3 = addPlatform(3200, 330, 90, 15); b3.bouncy = true;
        addPlatform(3350, 160, 120, 15); addSpikes(3365, 145, 3, 15);
        addSpikeChasing(150, H-65, 15, 1.5f); addSpikeChasing(2200, H-65, 15, 2.0f);
        addSpikes(265, H-65, 2, 15); addSpikes(525, H-65, 2, 15);
        addSpikes(795, H-65, 2, 15); addSpikes(1065, H-65, 2, 15);
        addSpikes(1335, H-65, 2, 15); addSpikes(1605, H-65, 2, 15);
        addSpikes(1875, H-65, 2, 15); addSpikes(2155, H-65, 2, 15);
        cannons.add(new Cannon(520, H-90, false, 90));
        cannons.add(new Cannon(1060, H-90, false, 80));
        cannons.add(new Cannon(1600, H-90, false, 75));
        cannons.add(new Cannon(2150, H-90, true, 85));
        cannons.add(new Cannon(2710, H-90, false, 70));
        cannons.add(new Cannon(3270, H-90, true, 80));
        goalX = 3700; goalY = H-110;
    }

    boolean goalSwitched = false;

    void buildLevel5() {
        levelW = 4400;
        goalSwitched = false;
        addPlatform(0, H-50, 260, 50); addPlatform(360, H-50, 200, 50);
        addPlatform(640, H-50, 200, 50); addPlatform(920, H-50, 200, 50);
        addPlatform(1200, H-50, 200, 50); addPlatform(1480, H-50, 200, 50);
        addPlatform(1760, H-50, 200, 50); addPlatform(2040, H-50, 200, 50);
        addPlatform(2320, H-50, 200, 50); addPlatform(2600, H-50, 200, 50);
        addPlatform(2880, H-50, 200, 50); addPlatform(3160, H-50, 200, 50);
        addPlatform(3440, H-50, 200, 50); addPlatform(3720, H-50, 600, 50);
        addPlatform(3600, H-180, 140, 15); addPlatform(3400, H-260, 140, 15);
        addPlatform(2600, H-220, 120, 15); addPlatform(900, H-220, 120, 15);
        addPlatform(460, H-180, 120, 15); addSpikes(480, H-195, 2, 15);
        Platform b1 = addPlatform(740, H-180, 120, 15); b1.bouncy = true;
        addPlatform(820, H-280, 130, 15); addSpikes(840, H-295, 3, 15);
        Platform fake1 = addPlatform(1020, H-180, 120, 15); fake1.fake = true;
        Platform mov1 = addPlatform(1300, H-180, 110, 15);
        mov1.moving = true; mov1.mx = 1300; mov1.mrange = 110; mov1.mspeed = 1.6f;
        Platform inv1 = addPlatform(1580, H-180, 120, 15); inv1.invisible = true;
        Platform shift1 = addPlatform(1860, H-180, 120, 15);
        shift1.shiftOnStep = true; shift1.shiftDist = 80;
        Platform mov2 = addPlatform(2140, H-180, 110, 15);
        mov2.moving = true; mov2.mx = 2140; mov2.mrange = 120; mov2.mspeed = 2.0f;
        Platform b2 = addPlatform(2420, H-180, 120, 15); b2.bouncy = true;
        addPlatform(2500, H-280, 130, 15); addSpikes(2520, H-295, 3, 15);
        Platform inv2 = addPlatform(2700, H-180, 120, 15); inv2.invisible = true;
        Platform shift2 = addPlatform(2980, H-180, 120, 15);
        shift2.shiftOnStep = true; shift2.shiftDist = -110;
        Platform mov3 = addPlatform(3160, H-190, 100, 15);
        mov3.moving = true; mov3.mx = 3160; mov3.mrange = 200; mov3.mspeed = 2.2f;
        addPlatform(3500, H-200, 110, 15); addSpikes(3515, H-215, 3, 15);
        addSpikes(370, H-65, 2, 15); addSpikes(930, H-65, 2, 15);
        addSpikes(1210, H-65, 2, 15); addSpikes(1770, H-65, 2, 15);
        addSpikes(2050, H-65, 2, 15); addSpikes(2610, H-65, 2, 15);
        addSpikes(2890, H-65, 2, 15); addSpikes(3170, H-65, 2, 15);
        addSpikes(3450, H-65, 2, 15);
        addSpikeChasing(-600, H-65, 15, 1.4f);
        cannons.add(new Cannon(1480, H-90, false, 110));
        cannons.add(new Cannon(2320, H-90, true, 100));
        cannons.add(new Cannon(2880, H-90, false, 95));
        cannons.add(new Cannon(3440, H-90, false, 90));
        goalX = 3900; goalY = H-110;
    }

    Platform addPlatform(int x, int y, int w, int h) {
        Platform p = new Platform(x, y, w, h);
        platforms.add(p); return p;
    }
    void addSpikes(int x, int y, int count, int size) {
        for(int i = 0; i < count; i++) spikes.add(new Spike(x + i * size, y, size, size));
    }
    Spike addSpikeChasing(int x, int y, int size, float speed) {
        Spike s = new Spike(x, y, size, size);
        s.chasing = true; s.cx = x; s.cy = y; s.cspeed = speed;
        spikes.add(s); return s;
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        tick++;
        dotBobTick++;
        blinkTick++;
        if(state == State.PLAYING) update();
        if(state == State.DYING) updateDeathAnim();
        if(state == State.TRANSITIONING) updateWipe();
        if(state == State.WIN_LEVEL) {
            winTimer++;
            if(winTimer > 100) {
                if(currentLevel + 1 > highestUnlockedLevel) {
                    highestUnlockedLevel = Math.min(currentLevel + 1, 4);
                }
                currentLevel++;
                if(currentLevel >= 5) state = State.WIN_GAME;
                else startLevel(currentLevel);
            }
        }
        repaint();
    }

    void updateWipe() {
        wipeTimer++;
        if(wipeTimer >= WIPE_TICKS) {
            state = wipeTargetState;
        }
    }

    float easeInOut(float t) {
        t = Math.max(0f, Math.min(1f, t));
        return t < 0.5f ? 4*t*t*t : 1 - (float)Math.pow(-2*t+2, 3)/2;
    }

    void updateDeathAnim() {
        deathAnimTimer++;
        if(screenShakeTick > 0) {
            screenShakeTick--;
            screenShakeX = (rng.nextFloat() - 0.5f) * 10f * (screenShakeTick / 18f);
            screenShakeY = (rng.nextFloat() - 0.5f) * 10f * (screenShakeTick / 18f);
        } else {
            screenShakeX = 0; screenShakeY = 0;
        }
        for(BodyPart bp : bodyParts) {
            bp.x += bp.vx; bp.y += bp.vy;
            bp.vy += 0.45f; bp.vx *= 0.98f;
            bp.rot += bp.rotV; bp.life--;
        }
        bodyParts.removeIf(bp -> bp.life <= 0);
        Iterator<Particle> it = particles.iterator();
        while(it.hasNext()) {
            Particle p = it.next();
            p.x += p.vx; p.y += p.vy; p.vy += 0.15f; p.life--;
            if(p.life <= 0) it.remove();
        }
        if(deathAnimTimer >= DEATH_ANIM_TICKS) {
            state = State.DEAD;
            bodyParts.clear();
        }
    }

    void update() {
        levelTimer++;
        if(currentLevel == 0) updateTutorialCards();
        if(jumpDown) jumpBufferTimer = JUMP_BUFFER;
        else if(jumpBufferTimer > 0) jumpBufferTimer--;

        float targetVX = 0;
        if(leftDown)       { targetVX = -MOVE_SPD; facingRight = false; }
        else if(rightDown) { targetVX =  MOVE_SPD; facingRight = true; }

        if(onGround) {
            pvx += (targetVX - pvx) * ACCEL;
            if(Math.abs(targetVX) < 0.1f) pvx *= FRICTION;
        } else {
            pvx += (targetVX - pvx) * (ACCEL * 0.6f);
            pvx *= AIR_FRICTION;
        }
        if(Math.abs(pvx) < 0.08f) pvx = 0;

        if(onGround) coyoteTimer = COYOTE_TIME;
        else if(coyoteTimer > 0) coyoteTimer--;

        wasOnGround = onGround;

        boolean canJump = coyoteTimer > 0 && !jumpConsumed;
        if(jumpBufferTimer > 0 && canJump) {
            pvy = JUMP_VEL; jumpConsumed = true;
            jumpBufferTimer = 0; coyoteTimer = 0;
            spawnDust();
        }
        if(!jumpDown) jumpConsumed = false;
        if(!jumpDown && pvy < -6f) pvy = Math.max(pvy + 1.2f, -6f);
        pvy = Math.min(pvy + GRAVITY, MAX_FALL);

        px += pvx;
        if(px < 0) px = 0;
        if(px > levelW - playerW) px = levelW - playerW;
        py += pvy;

        for(Spike s : spikes) {
            if(!s.chasing) continue;
            float dist = px - s.cx;
            if(Math.abs(dist) < 500) {
                float dir = dist > 0 ? 1 : -1;
                float proximity = 1f - Math.min(Math.abs(dist) / 400f, 0.8f);
                s.cx += dir * s.cspeed * (0.4f + proximity * 1.0f);
            }
            s.x = (int)s.cx;
        }

        for(Platform p : platforms) {
            if(p.moving) {
                p.x += p.mspeed * p.mdir;
                if(p.x > p.mx + p.mrange) p.mdir = -1;
                if(p.x < p.mx) p.mdir = 1;
            }
            if(p.fake && p.fakeTriggered) {
                p.fakeTimer++;
                if(p.fakeTimer > 25) p.y += 5;
            }
            if(p.shiftOnStep && p.shiftTriggered) {
                p.shiftTimer++;
                if(p.shiftTimer <= 20) p.x += p.shiftDist / 20;
            }
            if(p.invisible && p.revealTimer_active) {
                p.revealFlash--;
                if(p.revealFlash <= 0) p.revealTimer_active = false;
            }
        }

        onGround = false;
        Rectangle pr = new Rectangle((int)px, (int)py, playerW, playerH);
        for(Platform p : platforms) {
            if(p.fake && p.fakeTimer > 50) continue;
            Rectangle pl = p.rect();
            if(!pr.intersects(pl)) continue;
            if(pvy >= 0 && py + playerH - pvy <= pl.y + 8) {
                py = pl.y - playerH;
                if(p.bouncy) {
                    pvy = JUMP_VEL * 1.5f;
                    spawnBounceParticles((int)px, (int)py);
                } else {
                    pvy = 0; onGround = true;
                    if(p.fake && !p.fakeTriggered) p.fakeTriggered = true;
                    if(p.shiftOnStep && !p.shiftTriggered) p.shiftTriggered = true;
                    if(p.invisible) { p.revealTimer_active = true; p.revealFlash = 45; }
                }
            }
            else if(pvy < 0 && py - pvy >= pl.y + pl.height - 5) {
                py = pl.y + pl.height; pvy = 1;
            }
            else if(pvx > 0) { px = pl.x - playerW; pvx = 0; }
            else if(pvx < 0) { px = pl.x + pl.width; pvx = 0; }
        }

        if(py > H + 60) { killPlayer("Fell into a pit.", "Didn't jump. Incredible.", "GRAVITY"); return; }

        pr = new Rectangle((int)px + 3, (int)py + 2, playerW - 6, playerH - 2);
        for(Spike s : spikes) {
            if(pr.intersects(s.rect())) {
                if(s.chasing) killPlayer("Chasing spike caught up.", "They're faster than they look.", "RUN");
                else killPlayer("Walked into a spike.", "Happens to everyone.", "OUCH");
                return;
            }
        }

        for(Cannon c : cannons) {
            c.fireTimer++;
            if(c.fireTimer >= c.fireRate) {
                c.fireTimer = 0;
                float vel = c.facingRight ? 5f : -5f;
                cannonballs.add(new Cannonball(c.x + (c.facingRight ? 30 : -10), c.y + 10, vel));
            }
        }
        Iterator<Cannonball> cit = cannonballs.iterator();
        while(cit.hasNext()) {
            Cannonball cb = cit.next();
            cb.x += cb.vx;
            if(cb.x < -50 || cb.x > levelW + 50) { cit.remove(); continue; }
            if(pr.intersects(cb.rect())) {
                killPlayer("Got yeeted by a cannonball.", "Didn't see that coming.", "BOOM"); return;
            }
        }

        float targetCam = px - W / 2f + playerW / 2f;
        camX += (targetCam - camX) * 0.10f;
        camX = Math.max(0, Math.min(camX, levelW - W));

        if (currentLevel == 4 && !goalSwitched && px > 3700) {
            goalSwitched = true;
            goalX = 100; goalY = H - 110;
            for (Spike s : spikes) {
                if (s.chasing) { s.cx = levelW + 200; s.cspeed = -1.6f; }
            }
        }

        Rectangle goalRect = new Rectangle(goalX, goalY, GOAL_W, GOAL_H);
        if(new Rectangle((int)px, (int)py, playerW, playerH).intersects(goalRect)) {
            totalScore += (5 - currentLevel) * 300 + 500;
            spawnWinBurst();
            state = State.WIN_LEVEL;
        }

        Iterator<Particle> it = particles.iterator();
        while(it.hasNext()) {
            Particle p = it.next();
            p.x += p.vx; p.y += p.vy; p.vy += 0.15f; p.life--;
            if(p.life <= 0) it.remove();
        }
    }

    void updateTutorialCards() {
        if(activeTutCard != null) {
            activeTutCard.displayTimer++;
            if(activeTutCard.displayTimer >= TutorialCard.DISPLAY_TICKS) activeTutCard = null;
            return;
        }
        for(TutorialCard c : tutCards) {
            if(!c.shown && px >= c.triggerX) {
                c.shown = true; c.displayTimer = 0; activeTutCard = c; break;
            }
        }
    }

    void killPlayer(String l1, String l2, String tag) {
        if(state == State.DEAD || state == State.DYING) return;
        deaths++;
        if(l1 != null && l2 != null && tag != null) {
            currentDeathMsg = new String[]{l1, l2, tag};
        } else {
            int idx;
            do { idx = (int)(rng.nextFloat() * DEATH_MSGS.length); } while(idx == lastDeathMsg);
            lastDeathMsg = idx;
            currentDeathMsg = DEATH_MSGS[idx];
        }
        deathX = px; deathY = py;
        deathAnimTimer = 0; screenShakeTick = 18;
        spawnDeathAnimation();
        state = State.DYING;
    }

    void spawnDeathAnimation() {
        float cx = px + playerW / 2f;
        float cy = py + playerH / 2f;
        for(int i = 0; i < 20; i++) {
            float a = (float)(rng.nextDouble() * Math.PI * 2);
            float s = 3 + rng.nextFloat() * 6;
            particles.add(new Particle(cx, cy,
                (float)Math.cos(a)*s, (float)Math.sin(a)*s,
                18+rng.nextInt(15), new Color(255,200+rng.nextInt(55),80)));
        }
        for(int i = 0; i < 16; i++) {
            float a = (float)(rng.nextDouble() * Math.PI * 2);
            float s = 2 + rng.nextFloat() * 4;
            particles.add(new Particle(cx, cy,
                (float)Math.cos(a)*s, (float)Math.sin(a)*s,
                20+rng.nextInt(20), new Color(200+rng.nextInt(55),30+rng.nextInt(60),10)));
        }
        bodyParts.add(new BodyPart(cx-4, py-2,
            (rng.nextFloat()-0.5f)*4f, -9f-rng.nextFloat()*3f,
            0.18f+rng.nextFloat()*0.12f, 0, new Color(230,200,255)));
        bodyParts.add(new BodyPart(cx-3, py+14,
            -2f-rng.nextFloat()*2f, -5f-rng.nextFloat()*2f,
            -0.12f-rng.nextFloat()*0.1f, 1, new Color(220,220,255)));
        bodyParts.add(new BodyPart(cx-3, py+22,
            2f+rng.nextFloat()*2f, -3f-rng.nextFloat()*2f,
            0.14f+rng.nextFloat()*0.1f, 1, new Color(200,200,240)));
        bodyParts.add(new BodyPart(cx-5, py+playerH-10,
            -4f-rng.nextFloat()*3f, -4f-rng.nextFloat()*2f,
            -0.2f-rng.nextFloat()*0.15f, 2, new Color(60,60,100)));
        bodyParts.add(new BodyPart(cx+2, py+playerH-10,
            4f+rng.nextFloat()*3f, -5f-rng.nextFloat()*2f,
            0.2f+rng.nextFloat()*0.15f, 2, new Color(60,60,110)));
        bodyParts.add(new BodyPart(cx-8, py+18,
            -5f-rng.nextFloat()*3f, -3f-rng.nextFloat()*3f,
            -0.25f-rng.nextFloat()*0.1f, 3, new Color(180,180,230)));
        bodyParts.add(new BodyPart(cx+5, py+18,
            5f+rng.nextFloat()*3f, -4f-rng.nextFloat()*2f,
            0.22f+rng.nextFloat()*0.1f, 3, new Color(180,180,220)));
        for(int i = 0; i < 8; i++) {
            float a = (float)(rng.nextDouble() * Math.PI * 2);
            float spd = 2 + rng.nextFloat() * 5;
            particles.add(new Particle(cx+(rng.nextFloat()-0.5f)*8, cy+(rng.nextFloat()-0.5f)*8,
                (float)Math.cos(a)*spd, (float)Math.sin(a)*spd-2,
                25+rng.nextInt(20),
                new Color(150+rng.nextInt(105),100+rng.nextInt(100),200+rng.nextInt(55))));
        }
    }

    void spawnDeathBurst() {
        for(int i = 0; i < 28; i++) {
            float a = (float)(rng.nextDouble() * Math.PI * 2);
            float s = 1 + rng.nextFloat() * 5;
            particles.add(new Particle(px+playerW/2f, py+playerH/2f,
                (float)Math.cos(a)*s, (float)Math.sin(a)*s,
                25+rng.nextInt(20), new Color(100+rng.nextInt(80),0,rng.nextInt(80))));
        }
    }
    void spawnWinBurst() {
        for(int i = 0; i < 40; i++) {
            float a = (float)(rng.nextDouble() * Math.PI * 2);
            float s = 2 + rng.nextFloat() * 5;
            particles.add(new Particle(goalX+GOAL_W/2f, goalY,
                (float)Math.cos(a)*s, (float)Math.sin(a)*s,
                30+rng.nextInt(20), new Color(rng.nextInt(255),rng.nextInt(255),rng.nextInt(100))));
        }
    }
    void spawnDust() {
        for(int i = 0; i < 5; i++)
            particles.add(new Particle(px+playerW/2f, py+playerH,
                (rng.nextFloat()-0.5f)*3, -rng.nextFloat()*2,
                12, new Color(200,200,200,180)));
    }
    void spawnBounceParticles(int x, int y) {
        for(int i = 0; i < 10; i++) {
            float a = (float)(rng.nextDouble() * Math.PI);
            particles.add(new Particle(x+playerW/2f, y+playerH,
                (float)Math.cos(a)*4, (float)Math.sin(a)*-3,
                15, new Color(100,200,255)));
        }
    }

    @Override
    protected void paintComponent(Graphics g0) {
        super.paintComponent(g0);
        Graphics2D g = (Graphics2D)g0;
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        switch(state) {
            case MENU         -> drawMenu(g);
            case LEVEL_SELECT -> drawLevelSelect(g);
            case PLAYING      -> drawGame(g);
            case PAUSED       -> { drawGame(g); drawPauseMenu(g); }
            case DYING        -> { drawDeathAnim(g); }
            case DEAD         -> { drawGame(g); drawDeathScreen(g); }
            case WIN_LEVEL    -> { drawGame(g); drawWinLevel(g); }
            case WIN_GAME     -> drawWinGame(g);
            case TRANSITIONING -> drawWipeTransition(g);
        }
    }

    void drawWipeTransition(Graphics2D g) {
        float progress = easeInOut((float)wipeTimer / WIPE_TICKS);
        int edgeX = (int)(W * progress);
        Shape savedClip = g.getClip();
        g.setClip(0, 0, edgeX, H);
        drawGame(g);
        g.setClip(savedClip);
        AffineTransform saved = g.getTransform();
        int menuPushX = (int)(edgeX * 0.18f);
        g.translate(menuPushX, 0);
        g.setClip(edgeX - menuPushX, 0, W, H);
        drawMenu(g);
        g.setClip(savedClip);
        g.setTransform(saved);
        if(edgeX > 0 && edgeX < W) {
            int[] widths = {22, 14, 8, 4, 2};
            int[] alphas = { 18,  35, 60, 120, 220};
            for(int i = 0; i < widths.length; i++) {
                int hw = widths[i];
                g.setColor(new Color(180, 220, 255, alphas[i]));
                g.fillRect(edgeX - hw, 0, hw * 2, H);
            }
            g.setColor(new Color(230, 245, 255, 255));
            g.setStroke(new BasicStroke(1.5f));
            g.drawLine(edgeX, 0, edgeX, H);
            g.setStroke(new BasicStroke(1f));
            for(int si = 0; si < 8; si++) {
                int sy = (int)(((si * 137 + tick * 4) % (H + 30)) - 15);
                float sparkle = (float)(0.5 + 0.5 * Math.sin(tick * 0.25f + si * 0.9f));
                int sa = (int)(100 + 120 * sparkle);
                int ss = (int)(4 + 4 * sparkle);
                g.setColor(new Color(255, 255, 255, sa));
                g.fillOval(edgeX - ss/2, sy - ss/2, ss, ss);
            }
        }
        if(progress > 0.70f) {
            float labelAlpha = (progress - 0.70f) / 0.30f;
            int la = (int)(labelAlpha * 200);
            g.setFont(new Font("Courier New", Font.BOLD, 14));
            String lbl = "LEVEL " + (wipePendingLevel + 1) + " — " + LEVEL_NAMES[wipePendingLevel];
            int lw = g.getFontMetrics().stringWidth(lbl);
            g.setColor(new Color(0, 0, 0, la / 2));
            g.drawString(lbl, W/2 - lw/2 + 2, H - 28);
            g.setColor(new Color(100, 220, 255, la));
            g.drawString(lbl, W/2 - lw/2, H - 30);
        }
    }

    void drawDeathAnim(Graphics2D g) {
        AffineTransform old = g.getTransform();
        g.translate(screenShakeX, screenShakeY);
        drawGame(g);
        g.setTransform(old);
        if(deathAnimTimer < 8) {
            float flashAlpha = 1f - deathAnimTimer / 8f;
            g.setColor(new Color(255,200,50,(int)(flashAlpha*200)));
            g.fillRect(0,0,W,H);
        }
        for(BodyPart bp : bodyParts) {
            float lifeRatio = (float)bp.life / (DEATH_ANIM_TICKS + 10);
            int alpha = (int)(Math.min(1f, lifeRatio * 2f) * 255);
            if(alpha <= 0) continue;
            int bx = (int)(bp.x - camX + screenShakeX);
            int by = (int)(bp.y);
            AffineTransform at = g.getTransform();
            g.rotate(bp.rot, bx, by);
            Color c = new Color(bp.color.getRed(), bp.color.getGreen(), bp.color.getBlue(), alpha);
            g.setColor(c);
            switch(bp.type) {
                case 0 -> {
                    g.setColor(new Color(180,130,255,alpha/3));
                    g.fillOval(bx-8,by-8,20,20);
                    g.setColor(new Color(230,200,255,alpha));
                    g.fillOval(bx-4,by-4,12,12);
                    g.setColor(new Color(255,255,255,alpha));
                    g.fillOval(bx-1,by-1,5,5);
                }
                case 1 -> {
                    g.setColor(new Color(210,210,250,alpha));
                    g.fillRect(bx-4,by-5,8,10);
                    g.setColor(new Color(150,150,200,alpha/2));
                    g.fillRect(bx,by-5,2,10);
                }
                case 2 -> {
                    g.setColor(new Color(60,60,110,alpha));
                    g.fillRect(bx-2,by-6,5,10);
                }
                case 3 -> {
                    g.setColor(new Color(180,180,230,alpha));
                    g.fillRect(bx-5,by-2,10,3);
                }
            }
            g.setTransform(at);
        }
        for(Particle p : particles) {
            float alpha = (float)p.life / p.maxLife;
            int a = (int)(alpha * 255);
            if(a <= 0) continue;
            g.setColor(new Color(p.color.getRed(),p.color.getGreen(),p.color.getBlue(),a));
            int px2 = (int)(p.x - camX + screenShakeX);
            int py2 = (int)(p.y);
            int sz = (int)(4 * alpha) + 2;
            g.fillOval(px2-sz/2, py2-sz/2, sz, sz);
        }
        if(deathAnimTimer > 25) {
            float v = (deathAnimTimer - 25f) / (DEATH_ANIM_TICKS - 25f);
            g.setColor(new Color(0,0,0,(int)(v*100)));
            g.fillRect(0,0,W,H);
        }
    }

    void initMenuParticles() {
        for (int i = 0; i < menuParticleX.length; i++) {
            menuParticleX[i]  = rng.nextFloat() * W;
            menuParticleY[i]  = rng.nextFloat() * H;
            menuParticleVY[i] = -(0.3f + rng.nextFloat() * 0.9f);
            menuParticleSz[i] = 2f + rng.nextFloat() * 5f;
            menuParticleHue[i]= rng.nextInt(360);
        }
        menuParticlesInit = true;
    }

    void updateMenuParticles() {
        for (int i = 0; i < menuParticleY.length; i++) {
            menuParticleY[i] += menuParticleVY[i];
            menuParticleX[i] += (float) Math.sin(tick * 0.012f + i * 0.7f) * 0.4f;
            if (menuParticleY[i] < -10) {
                menuParticleY[i] = H + 5;
                menuParticleX[i] = rng.nextFloat() * W;
            }
        }
        glitchTick++;
        if (glitchTimer > 0) {
            glitchTimer--;
            glitchOffX = (rng.nextFloat() - 0.5f) * 6f;
            if (glitchTimer == 0) glitchOffX = 0;
        } else if (glitchTick % 240 == 0) {
            glitchTimer = 5 + rng.nextInt(6);
        }
        tickerOffset--;
    }

    void drawMenu(Graphics2D g) {
        if (!menuParticlesInit) initMenuParticles();
        updateMenuParticles();

        float t = tick * 0.01f;

        Color bg1 = new Color(
            (int)(10 + 8 * Math.sin(t)),
            (int)(3  + 4 * Math.sin(t + 1)),
            (int)(28 + 12 * Math.sin(t + 2)));
        Color bg2 = new Color(
            (int)(35 + 18 * Math.sin(t + 3)),
            (int)(6  +  6 * Math.sin(t + 4)),
            (int)(65 + 22 * Math.sin(t + 5)));
        g.setPaint(new GradientPaint(0, 0, bg1, W, H, bg2));
        g.fillRect(0, 0, W, H);

        float[] vFrac = {0f, 0.4f, 1f};
        Color[] vCol  = {new Color(0,0,0,0), new Color(0,0,0,0), new Color(0,0,0,120)};
        g.setPaint(new RadialGradientPaint(W/2f, H/2f, W*0.75f, vFrac, vCol));
        g.fillRect(0, 0, W, H);
        g.setPaint(null);

        for (int sy = 0; sy < H; sy += 4) {
            g.setColor(new Color(0, 0, 0, 26));
            g.fillRect(0, sy, W, 2);
        }

        for (int i = 0; i < menuParticleX.length; i++) {
            float life = (float)(0.5 + 0.5 * Math.sin(tick * 0.04f + i * 0.53f));
            int   alpha = (int)(40 + 120 * life);
            float sz    = menuParticleSz[i] * (0.7f + 0.3f * life);
            int hue = (menuParticleHue[i] + tick / 3) % 360;
            Color pc = Color.getHSBColor(hue / 360f, 0.6f, 1.0f);
            g.setColor(new Color(pc.getRed(), pc.getGreen(), pc.getBlue(), alpha / 4));
            g.fillOval((int)(menuParticleX[i] - sz * 1.8f), (int)(menuParticleY[i] - sz * 1.8f),
                       (int)(sz * 3.6f), (int)(sz * 3.6f));
            g.setColor(new Color(pc.getRed(), pc.getGreen(), pc.getBlue(), alpha));
            g.fillOval((int)(menuParticleX[i] - sz / 2), (int)(menuParticleY[i] - sz / 2),
                    (int)sz, (int)sz);
        }

        drawCornerFlourishMenu(g, 0, 0, false, false);
        drawCornerFlourishMenu(g, W, 0, true,  false);
        drawCornerFlourishMenu(g, 0, H, false, true);
        drawCornerFlourishMenu(g, W, H, true,  true);

        float lineAlpha = 0.35f + 0.15f * (float)Math.sin(tick * 0.05f);
        g.setColor(new Color(120, 60, 220, (int)(lineAlpha * 255)));
        g.setStroke(new BasicStroke(1.2f));
        g.drawLine(30, 153, W - 30, 153);
        g.drawLine(30, 156, W - 30, 156);
        g.setColor(new Color(80, 30, 160, (int)(lineAlpha * 170)));
        g.drawLine(30, 158, W - 30, 158);
        g.setStroke(new BasicStroke(1f));

        g.setFont(new Font("Courier New", Font.BOLD, 58));
        String title = "i   LOST";
        int titleW = g.getFontMetrics().stringWidth(title);
        int titleX = W / 2 - titleW / 2;
        int titleY = 126;

        for (int gi = 6; gi >= 1; gi--) {
            float glowT = (float)(0.5 + 0.5 * Math.sin(tick * 0.06f));
            int gAlpha  = (int)((7 + 9 * glowT) * (7 - gi));
            int gOff    = gi * 3;
            g.setColor(new Color(255, 50, 70, Math.min(255, gAlpha)));
            g.drawString(title, titleX - gOff + (int) glitchOffX, titleY + gOff);
            g.drawString(title, titleX + gOff + (int) glitchOffX, titleY - gOff);
        }

        if (glitchTimer > 0) {
            g.setColor(new Color(0, 220, 255, 75));
            g.drawString(title, titleX - 3 + (int) glitchOffX, titleY);
            g.setColor(new Color(255, 40, 80, 75));
            g.drawString(title, titleX + 3 + (int) glitchOffX, titleY);
            int tearY = titleY - 28 + rng.nextInt(42);
            g.setColor(new Color(255, 255, 255, 35 + rng.nextInt(35)));
            g.fillRect(titleX - 8, tearY, titleW + 16, 2 + rng.nextInt(4));
        }

        drawShadowText(g, title, titleX + (int) glitchOffX, titleY,
                    new Color(255, 85, 95), new Color(105, 0, 10));

        float shimmerPos = (tick % 110) / 110f;
        int shimX = (int)(titleX + shimmerPos * (titleW + 40)) - 20;
        g.setPaint(new GradientPaint(
            shimX, 0, new Color(255, 255, 255, 0),
            shimX + 38, 0, new Color(255, 230, 255, 85)));
        g.setFont(new Font("Courier New", Font.BOLD, 58));
        g.drawString(title, titleX + (int) glitchOffX, titleY);
        g.setPaint(null);

        g.setFont(new Font("Courier New", Font.PLAIN, 15));
        g.setColor(new Color(190, 190, 215, (int)(175 + 60 * Math.sin(tick * 0.04f))));
        String sub = "A normal platformer.  Keep calm and keep on going!";
        g.drawString(sub, W / 2 - g.getFontMetrics().stringWidth(sub) / 2, 160);

        int charX = W / 2 - 7, charY = 183;

        for (int ri = 0; ri < 3; ri++) {
            float phase = (tick * 0.045f + ri * 2.1f) % (float)(Math.PI * 2);
            float r = 20f + 15f * (float)Math.sin(phase);
            float a = 0.4f - 0.35f * ((float)Math.sin(phase) * 0.5f + 0.5f);
            g.setColor(new Color(155, 90, 255, (int)(a * 255)));
            g.setStroke(new BasicStroke(1.4f));
            g.drawOval((int)(charX + playerW / 2f - r),
                    (int)(charY + playerH / 2f - r),
                       (int)(r * 2), (int)(r * 2));
            g.setStroke(new BasicStroke(1f));
        }
        float haloR = 34f + 6f * (float)Math.sin(tick * 0.07f);
        g.setColor(new Color(125, 75, 215, 32));
        g.fillOval((int)(charX + playerW / 2f - haloR),
                (int)(charY + playerH / 2f - haloR),
                   (int)(haloR * 2), (int)(haloR * 2));

        for (int oi = 0; oi < 5; oi++) {
            double angle = tick * 0.03 + oi * (Math.PI * 2 / 5);
            int ox = (int)(charX + playerW / 2f + 33 * Math.cos(angle));
            int oy = (int)(charY + playerH / 2f + 27 * Math.sin(angle));
            float bright = (float)(0.6 + 0.4 * Math.sin(tick * 0.1 + oi));
            g.setColor(new Color(255, 230, 120, (int)(bright * 200)));
            int ss = (int)(3 + 2 * bright);
            g.fillOval(ox - ss / 2, oy - ss / 2, ss, ss);
        }

        drawPlayerChar(g, charX, charY, true, (tick / 8) % 4);

        int optBaseY = 284;
        int optSpacing = 54;
        for (int i = 0; i < MENU_OPTIONS.length; i++) {
            boolean sel = (menuSel == i);
            int oy = optBaseY + i * optSpacing;

            if (sel) {
                float sweep = (float)((Math.sin(tick * 0.09f) + 1) * 0.5f);
                int barX = W / 2 - 148;
                g.setPaint(new GradientPaint(
                    barX, oy - 27, new Color(255, 200, 60, 0),
                    barX + (int)(296 * sweep) + 1, oy - 27, new Color(255, 200, 60, 48)));
                g.fillRoundRect(barX, oy - 29, 296, 38, 10, 10);
                g.setPaint(null);
                g.setColor(new Color(255, 200, 60, 85));
                g.setStroke(new BasicStroke(1.7f));
                g.drawRoundRect(barX, oy - 29, 296, 38, 10, 10);
                g.setStroke(new BasicStroke(1f));
                g.setColor(new Color(255, 200, 60));
                g.fillRect(barX - 6, oy - 12, 4, 22);
            }

            g.setFont(new Font("Courier New", Font.BOLD, sel ? 26 : 22));
            Color col = sel ? new Color(255, 212, 80) : new Color(152, 142, 192);
            g.setColor(new Color(0, 0, 0, 80));
            g.drawString(MENU_OPTIONS[i],
                        W / 2 - g.getFontMetrics().stringWidth(MENU_OPTIONS[i]) / 2 + 2,
                        oy + 2);
            g.setColor(col);
            g.drawString(MENU_OPTIONS[i],
                        W / 2 - g.getFontMetrics().stringWidth(MENU_OPTIONS[i]) / 2,
                        oy);
        }

        // ── Scrolling tip ticker ──────────────────────────────
        String[] tickers = {
            "  ✦  A/D or ←→ to move   ",
            "  ✦  SPACE/W/↑ to jump   ",
            "  ✦  R to restart level   ",
            "  ✦  ESC to pause   ",
            "  ✦  Complete levels to unlock more   ",
            "  ✦  Watch out for chasing spikes!   ",
            "  ✦  Invisible platforms sparkle faintly   ",
            "  ✦  Cyan platforms launch you high   ",
            "  ✦  Fake platforms crumble on contact   ",
        };
        StringBuilder tb = new StringBuilder();
        for (String s : tickers) tb.append(s);
        String fullTicker = tb.toString() + tb.toString();

        g.setColor(new Color(0, 0, 0, 110));
        g.fillRect(0, H - 44, W, 44);
        g.setColor(new Color(80, 50, 140, 150));
        g.setStroke(new BasicStroke(1f));
        g.drawLine(0, H - 44, W, H - 44);

        g.setFont(new Font("Courier New", Font.PLAIN, 12));
        int textLen = g.getFontMetrics().stringWidth(fullTicker);
        int offset = ((tickerOffset % textLen) + textLen) % textLen;
        g.setColor(new Color(175, 155, 215));
        g.setClip(0, H - 44, W, 44);
        g.drawString(fullTicker, -offset, H - 24);
        g.drawString(fullTicker, -offset + textLen, H - 24);
        g.setClip(null);

        // ── Navigate/confirm hint with scrolling animation ────
        g.setFont(new Font("Courier New", Font.PLAIN, 11));
        String ctrl = "↑↓  Navigate     ENTER  Confirm";
        int ctrlLen = g.getFontMetrics().stringWidth(ctrl);
        int ctrlOffset = ((tickerOffset % ctrlLen) + ctrlLen) % ctrlLen;
        g.setColor(new Color(135, 125, 162));
        g.setClip(0, H - 50, W, 20);
        g.drawString(ctrl, -ctrlOffset + W / 2 - ctrlLen / 2, H - 47);
        g.drawString(ctrl, -ctrlOffset + W / 2 - ctrlLen / 2 + ctrlLen, H - 47);
        g.setClip(null);

        // ── Legend pills ──────────────────────────────────────
        String[] legend = { "Red crumble = Fake", "Cyan ↑↑ = Bouncy",
                            "Sparkle = Invisible", "Red trail = Chasing" };
        int pillY = 456;
        g.setFont(new Font("Courier New", Font.PLAIN, 11));
        int pillTotalW = 0;
        int[] pillWidths = new int[legend.length];
        for (int i = 0; i < legend.length; i++) {
            pillWidths[i] = g.getFontMetrics().stringWidth(legend[i]) + 16;
            pillTotalW += pillWidths[i] + (i < legend.length - 1 ? 8 : 0);
        }
        int pillX = W / 2 - pillTotalW / 2;
        for (int i = 0; i < legend.length; i++) {
            g.setColor(new Color(55, 38, 95, 135));
            g.fillRoundRect(pillX, pillY - 14, pillWidths[i], 20, 8, 8);
            g.setColor(new Color(115, 85, 175, 170));
            g.setStroke(new BasicStroke(1f));
            g.drawRoundRect(pillX, pillY - 14, pillWidths[i], 20, 8, 8);
            g.setColor(new Color(188, 168, 218));
            g.drawString(legend[i], pillX + 8, pillY);
            pillX += pillWidths[i] + 8;
        }
    }

    void drawCornerFlourishMenu(Graphics2D g, int cx, int cy, boolean flipX, boolean flipY) {
        AffineTransform old = g.getTransform();
        g.translate(cx, cy);
        if (flipX) g.scale(-1, 1);
        if (flipY) g.scale(1, -1);

        float pulse = 0.5f + 0.5f * (float) Math.sin(tick * 0.03f);

        g.setColor(new Color(155, 95, 255, (int)(58 + 38 * pulse)));
        g.setStroke(new BasicStroke(1.8f));
        g.drawLine(8, 8, 8, 52);
        g.drawLine(8, 8, 52, 8);
        g.setStroke(new BasicStroke(1f));
        g.setColor(new Color(195, 145, 255, (int)(28 + 18 * pulse)));
        g.setStroke(new BasicStroke(1f));
        g.drawLine(14, 14, 14, 40);
        g.drawLine(14, 14, 40, 14);
        g.setColor(new Color(200, 160, 255, (int)(50 + 30 * pulse)));
        g.drawLine(8, 30, 14, 30);
        g.drawLine(30, 8, 30, 14);
        g.setStroke(new BasicStroke(1f));
        g.setColor(new Color(215, 175, 255, (int)(135 + 100 * pulse)));
        g.fillOval(4, 4, 8, 8);
        g.setColor(new Color(255, 245, 255, 195));
        g.fillOval(6, 6, 4, 4);

        g.setTransform(old);
    }

    void drawLevelSelect(Graphics2D g) {
        float t = tick * 0.01f;
        Color c1 = new Color((int)(15+8*Math.sin(t)),(int)(20+10*Math.sin(t+1)),(int)(35+12*Math.sin(t+2)));
        Color c2 = new Color((int)(40+15*Math.sin(t+3)),(int)(30+12*Math.sin(t+4)),(int)(70+18*Math.sin(t+5)));
        g.setPaint(new GradientPaint(0,0,c1,W,H,c2));
        g.fillRect(0,0,W,H);
        g.setFont(new Font("Courier New", Font.BOLD, 36));
        String title = "SELECT LEVEL";
        drawShadowText(g, title, W/2-g.getFontMetrics().stringWidth(title)/2, 70,
            new Color(100,200,255), new Color(30,80,120));
        int cardW=140, cardH=160, spacing=20;
        int totalW = 5*cardW + 4*spacing;
        int startX = W/2 - totalW/2;
        int cardY = 120;
        for(int i = 0; i < 5; i++) {
            int cx = startX + i*(cardW+spacing);
            boolean selected = (levelSelectSel == i);
            boolean unlocked = (i <= highestUnlockedLevel);
            g.setColor(new Color(0,0,0,80));
            g.fillRoundRect(cx+4,cardY+4,cardW,cardH,12,12);
            if(unlocked) g.setColor(selected ? new Color(40,60,100,240) : new Color(25,35,60,220));
            else g.setColor(new Color(30,30,40,200));
            g.fillRoundRect(cx,cardY,cardW,cardH,12,12);
            if(selected && unlocked) { g.setColor(new Color(255,200,80,200)); g.setStroke(new BasicStroke(3f)); }
            else if(unlocked) { g.setColor(new Color(100,140,200,150)); g.setStroke(new BasicStroke(1.5f)); }
            else { g.setColor(new Color(60,60,80,150)); g.setStroke(new BasicStroke(1.5f)); }
            g.drawRoundRect(cx,cardY,cardW,cardH,12,12);
            g.setStroke(new BasicStroke(1f));
            g.setFont(new Font("Courier New", Font.BOLD, 48));
            String num = String.valueOf(i+1);
            int numW = g.getFontMetrics().stringWidth(num);
            g.setColor(unlocked ? (selected ? new Color(255,220,100) : new Color(180,200,255)) : new Color(80,80,100));
            g.drawString(num, cx+cardW/2-numW/2, cardY+60);
            g.setFont(new Font("Courier New", Font.BOLD, 11));
            String name = LEVEL_NAMES[i];
            int nameW = g.getFontMetrics().stringWidth(name);
            g.setColor(unlocked ? (selected ? new Color(255,240,200) : new Color(160,180,220)) : new Color(70,70,90));
            g.drawString(name, cx+cardW/2-nameW/2, cardY+85);
            if(!unlocked) {
                g.setFont(new Font("Courier New", Font.BOLD, 28));
                g.setColor(new Color(100,80,120));
                String lock = "🔒";
                g.drawString(lock, cx+cardW/2-g.getFontMetrics().stringWidth(lock)/2, cardY+125);
            } else {
                g.setFont(new Font("Courier New", Font.PLAIN, 10));
                g.setColor(new Color(140,140,180));
                String diff = getDifficultyLabel(i);
                g.drawString(diff, cx+cardW/2-g.getFontMetrics().stringWidth(diff)/2, cardY+105);
                if(i < highestUnlockedLevel || (i==4 && highestUnlockedLevel>=4)) {
                    g.setColor(new Color(100,255,150));
                    g.setFont(new Font("Courier New", Font.BOLD, 16));
                    String check = "✓";
                    g.drawString(check, cx+cardW/2-g.getFontMetrics().stringWidth(check)/2, cardY+140);
                }
            }
            if(selected && unlocked) {
                g.setColor(new Color(255,200,80,(int)(100+50*Math.sin(tick*0.1))));
                g.fillRoundRect(cx-2,cardY-2,cardW+4,4,2,2);
            }
        }
        g.setFont(new Font("Courier New", Font.PLAIN, 13));
        g.setColor(new Color(180,180,210));
        String inst = "← →  Select Level   |   ENTER / SPACE  Start   |   ESC  Back";
        g.drawString(inst, W/2-g.getFontMetrics().stringWidth(inst)/2, cardY+cardH+50);
        if(levelSelectSel > highestUnlockedLevel) {
            g.setFont(new Font("Courier New", Font.ITALIC, 12));
            g.setColor(new Color(255,150,100));
            String lockHint = "Complete previous levels to unlock!";
            g.drawString(lockHint, W/2-g.getFontMetrics().stringWidth(lockHint)/2, cardY+cardH+75);
        }
        g.setFont(new Font("Courier New", Font.PLAIN, 11));
        g.setColor(new Color(140,160,200));
        String progress = "Progress: " + (highestUnlockedLevel+1) + "/5 levels unlocked";
        g.drawString(progress, W/2-g.getFontMetrics().stringWidth(progress)/2, H-40);
    }

    String getDifficultyLabel(int level) {
        return switch(level) {
            case 0 -> "★☆☆☆☆ Easy";
            case 1 -> "★★☆☆☆ Medium";
            case 2 -> "★★★☆☆ Hard";
            case 3 -> "★★★★☆ Harder";
            case 4 -> "★★★★★ Expert";
            default -> "";
        };
    }

    void drawGame(Graphics2D g) {
        int cx = (int)camX;

        g.setPaint(new GradientPaint(0, 0, new Color(8, 8, 35), 0, H, new Color(20, 18, 55)));
        g.fillRect(0, 0, W, H);

        int moonX = W - 130, moonY = 45;
        for (int gi = 4; gi >= 1; gi--) {
            int gr = gi * 14;
            int ga = 10 + gi * 5;
            g.setColor(new Color(220, 230, 160, ga));
            g.fillOval(moonX - gr, moonY - gr, 60 + gr * 2, 60 + gr * 2);
        }
        g.setColor(new Color(255, 248, 195));
        g.fillOval(moonX, moonY, 60, 60);
        g.setColor(new Color(230, 218, 155, 160));
        g.fillOval(moonX + 12, moonY + 10, 14, 12);
        g.fillOval(moonX + 30, moonY + 28, 10,  9);
        g.fillOval(moonX +  8, moonY + 32,  8,  7);
        g.setColor(new Color(255, 255, 220, 90));
        g.setStroke(new BasicStroke(2.5f));
        g.drawArc(moonX + 4, moonY + 4, 52, 52, 40, 120);
        g.setStroke(new BasicStroke(1f));

        for (int i = 0; i < 90; i++) {
            int sx = (((i * 137 + i * 29) % 4000) - (int)(cx * 0.05f) % 4000 + 8000) % W;
            int sy = (i * 61 + (i % 7) * 13) % (H - 120);
            boolean twinkle = ((i + tick / 12) % 5 == 0);
            int starAlpha = twinkle ? 255 : (120 + (i % 3) * 30);
            int starSize  = (i % 11 == 0) ? 2 : 1;
            g.setColor(new Color(220, 225, 255, starAlpha));
            g.fillRect(sx, sy, starSize, starSize);
            if (i % 17 == 0) {
                g.setColor(new Color(220, 225, 255, 55));
                g.drawLine(sx - 3, sy, sx + 3, sy);
                g.drawLine(sx, sy - 3, sx, sy + 3);
            }
        }
        for (int i = 0; i < 40; i++) {
            int sx = (((i * 211 + 500) % 4200) - (int)(cx * 0.12f) % 4200 + 8400) % W;
            int sy = (i * 83 + 20) % (H / 2 - 30);
            g.setColor(new Color(255, 240, 210, 160 + (i % 4) * 20));
            g.fillRect(sx, sy, 1, 1);
        }

        {
            int off1 = (int)(cx * 0.20f);
            int[] buildingHeights = {70, 110, 80, 130, 60, 100, 90, 75, 120, 95, 65, 140, 85, 70, 105};
            int bw = 68;
            int totalBuildings = 22;
            int repeatW = bw * totalBuildings;
            for (int i = 0; i < totalBuildings; i++) {
                int bh  = buildingHeights[i % buildingHeights.length];
                int bx2 = ((i * bw) - (off1 % repeatW) + repeatW * 2) % repeatW;
                for (int tile = 0; tile <= W / repeatW + 1; tile++) {
                    int drawX = bx2 + tile * repeatW - repeatW;
                    if (drawX + bw < 0 || drawX > W) continue;
                    int by2 = H - 80 - bh;
                    g.setColor(new Color(14, 12, 30));
                    g.fillRect(drawX, by2, bw - 2, bh + 80);
                    for (int wy = by2 + 6; wy < H - 90; wy += 10) {
                        for (int wx2 = drawX + 5; wx2 < drawX + bw - 8; wx2 += 10) {
                            int seed = (i * 1000 + (wy - by2) * 50 + (wx2 - drawX));
                            boolean lit  = ((long)(seed * 2654435769L) & 0xFFFFL) > 40000L;
                            boolean warm = ((long)(seed * 1234567891L) & 0xFFFFL) > 32768L;
                            if (lit) {
                                g.setColor(warm ? new Color(255, 230, 100, 130) : new Color(160, 210, 255, 110));
                                g.fillRect(wx2, wy, 5, 4);
                            }
                        }
                    }
                    if (i % 3 == 0) {
                        g.setColor(new Color(20, 15, 40));
                        g.fillRect(drawX + bw / 2 - 1, by2 - 16, 2, 16);
                        if ((tick / 30 + i) % 2 == 0) {
                            g.setColor(new Color(255, 60, 60, 200));
                            g.fillOval(drawX + bw / 2 - 3, by2 - 20, 6, 6);
                        }
                    }
                }
            }
        }

        {
            int off2 = (int)(cx * 0.40f);
            int[] midHeights = {55, 90, 45, 75, 100, 60, 80, 50, 95, 70, 85, 40, 110, 65};
            int mw = 52;
            int totalMid = 28;
            int repeatW2 = mw * totalMid;
            for (int i = 0; i < totalMid; i++) {
                int bh  = midHeights[i % midHeights.length];
                int bx2 = ((i * mw) - (off2 % repeatW2) + repeatW2 * 2) % repeatW2;
                for (int tile = 0; tile <= W / repeatW2 + 1; tile++) {
                    int drawX = bx2 + tile * repeatW2 - repeatW2;
                    if (drawX + mw < 0 || drawX > W) continue;
                    int by2 = H - 65 - bh;
                    g.setColor(new Color(18, 15, 40));
                    g.fillRect(drawX, by2, mw - 3, bh + 65);
                    for (int wy = by2 + 5; wy < H - 75; wy += 9) {
                        for (int wx2 = drawX + 4; wx2 < drawX + mw - 5; wx2 += 9) {
                            int seed = (i * 777 + (wy - by2) * 31 + (wx2 - drawX));
                            boolean lit  = ((long)(seed * 2654435769L) & 0xFFFFL) > 36000L;
                            boolean warm = ((long)(seed * 987654321L)  & 0xFFFFL) > 32768L;
                            if (lit) {
                                g.setColor(warm ? new Color(255, 210, 90, 160) : new Color(130, 190, 255, 140));
                                g.fillRect(wx2, wy, 4, 3);
                            }
                        }
                    }
                }
            }
        }

        {
            int off3 = (int)(cx * 0.30f);
            g.setColor(new Color(12, 10, 28));
            int totalTrees = 55;
            int treeRepeat = 53 * totalTrees;
            for (int i = 0; i < totalTrees; i++) {
                int tx = ((i * 53 + i * 17) - (off3 % treeRepeat) + treeRepeat * 2) % treeRepeat;
                for (int tile = 0; tile <= W / treeRepeat + 1; tile++) {
                    int drawX = tx + tile * treeRepeat - treeRepeat;
                    if (drawX < -30 || drawX > W + 30) continue;
                    int th = 30 + (i * 19 % 25);
                    int tw = 18 + (i * 11 % 14);
                    g.fillOval(drawX - tw / 2, H - 68 - th, tw, th);
                    g.fillRect(drawX - 2, H - 68, 4, 10);
                }
            }
        }

        g.setPaint(new GradientPaint(0, H - 55, new Color(12, 8, 25), 0, H, new Color(6, 4, 15)));
        g.fillRect(0, H - 55, W, 55);
        for (int fi = 0; fi < 3; fi++) {
            g.setColor(new Color(60, 40, 120, 10 - fi * 3));
            g.fillRect(0, H - 60 - fi * 8, W, 12);
        }

        float[] bloomFractions = {0f, 1f};
        Color[] bloomColors = {new Color(80, 90, 40, 18), new Color(0, 0, 0, 0)};
        g.setPaint(new RadialGradientPaint(moonX + 30, moonY + 30, 160, bloomFractions, bloomColors));
        g.fillRect(moonX - 130, moonY - 30, 320, 220);

        if(currentLevel == 0) drawTutorialZoneMarkers(g, cx);

        for(Platform p : platforms) {
            int rx = p.x - cx;
            if(rx + p.w < -10 || rx > W + 10) continue;
            if(p.invisible) {
                if(p.revealTimer_active) {
                    g.setColor(new Color(180,180,255,180));
                    g.fillRect(rx, p.y, p.w, p.h);
                    g.setColor(new Color(220,200,255,230));
                    g.fillRect(rx, p.y, p.w, 4);
                } else {
                    float sparkle = (float)(0.3f + 0.15f * Math.sin(tick * 0.08f + p.x * 0.01f));
                    g.setColor(new Color(160,140,255,(int)(sparkle * 80)));
                    for(int sx2 = 0; sx2 < p.w; sx2 += 12) {
                        if((sx2/12 + blinkTick/8) % 3 == 0)
                            g.fillOval(rx + sx2, p.y, 5, 5);
                    }
                }
                continue;
            }
            if(p.fake && p.fakeTriggered) {
                float crumble = Math.min(1f, p.fakeTimer / 25f);
                g.setColor(new Color((int)(40 + crumble*180), (int)(25*(1-crumble)), 10));
                g.fillRect(rx, p.y, p.w, p.h);
                g.setColor(new Color(200, 50, 0));
                for(int i = 0; i < 3; i++)
                    g.drawLine(rx + i*p.w/3, p.y, rx + i*p.w/3 + 5, p.y + p.h);
            } else if(p.fake) {
                Color topCol = new Color(60 + currentLevel*8, 100 - currentLevel*4, 60 + currentLevel*4);
                g.setColor(new Color(50, 35, 15));
                g.fillRect(rx, p.y + 7, p.w, p.h - 7);
                g.setColor(topCol);
                g.fillRect(rx, p.y, p.w, 7);
            } else if(p.bouncy) {
                float bounce = 0.5f + 0.5f * (float)Math.sin(tick * 0.2f);
                int b = (int)(50 + 80 * bounce);
                g.setColor(new Color(b, (int)(150 + 50*bounce), (int)(200 + 50*bounce)));
                g.fillRect(rx, p.y, p.w, p.h);
                g.setColor(new Color(150, 255, 255));
                g.fillRect(rx, p.y, p.w, 4);
                g.setColor(new Color(100, 220, 255, (int)(60 + 40*bounce)));
                g.fillRect(rx + 2, p.y + 4, p.w - 4, p.h - 6);
                g.setColor(Color.WHITE);
                g.setFont(new Font("Courier New", Font.BOLD, 9));
                if(p.w > 40) g.drawString("↑↑↑", rx + p.w/2 - 10, p.y - 2);
            } else {
                Color topCol = new Color(60 + currentLevel*8, 100 - currentLevel*4, 60 + currentLevel*4);
                g.setColor(new Color(50, 35, 15));
                g.fillRect(rx, p.y + 7, p.w, p.h - 7);
                g.setColor(topCol);
                g.fillRect(rx, p.y, p.w, 7);
                g.setColor(new Color(Math.min(255,80+currentLevel*10), Math.min(255,130-currentLevel*5), Math.min(255,80+currentLevel*5), 120));
                g.fillRect(rx + 2, p.y, p.w - 4, 2);
            }
        }

        for(Spike s : spikes) {
            int sx = s.x - cx;
            if(sx < -30 || sx > W + 30) continue;
            if(s.chasing) {
                float pulse = 0.5f + 0.5f * (float)Math.sin(tick * 0.15f);
                g.setColor(new Color(255,50,0,(int)(40+30*pulse)));
                g.fillOval(sx-12,s.y-12,s.w+24,s.h+24);
                g.setColor(new Color(255,80,0,(int)(50+30*pulse)));
                g.fillOval(sx-6,s.y-6,s.w+12,s.h+12);
                g.setColor(new Color(255,50,0,35));
                g.fillRect(sx-20,s.y,14,s.h);
                g.setColor(new Color(255,100,40));
            } else {
                g.setColor(new Color(210,220,255));
            }
            int[] xp = {sx, sx+s.w/2, sx+s.w};
            int[] yp = {s.y+s.h, s.y, s.y+s.h};
            g.fillPolygon(xp, yp, 3);
            g.setColor(s.chasing ? new Color(255,160,80) : new Color(180,200,255));
            g.drawLine(xp[0], yp[0], xp[1], yp[1]);
            g.setColor(s.chasing ? new Color(255,200,150,120) : new Color(240,245,255,150));
            g.fillPolygon(new int[]{sx+s.w/2-2,sx+s.w/2,sx+s.w/2+2},
                        new int[]{s.y+s.h, s.y+3, s.y+s.h}, 3);
        }

        for(Cannon c : cannons) {
            int ccx = c.x - cx;
            if(ccx < -60 || ccx > W + 60) continue;
            drawCannon(g, ccx, c.y, c.facingRight);
        }

        for(Cannonball cb : cannonballs) {
            int bx = (int)(cb.x - cx);
            if(bx < -20 || bx > W + 20) continue;
            float trailStep = cb.vx < 0 ? 7 : -7;
            for(int ti = 1; ti <= 3; ti++) {
                int tx = bx + (int)(trailStep * ti);
                g.setColor(new Color(200,80,0,60-ti*15));
                g.fillOval(tx-5,(int)cb.y-5,10,10);
            }
            g.setColor(new Color(255,120,0,70));
            g.fillOval(bx-10,(int)cb.y-10,20,20);
            g.setColor(new Color(40,40,50));
            g.fillOval(bx-6,(int)cb.y-6,12,12);
            g.setColor(new Color(120,120,140));
            g.fillOval(bx-3,(int)cb.y-4,5,4);
        }

        drawGoal(g, goalX - cx, goalY);

        for(Particle p : particles) {
            float alpha = (float)p.life / p.maxLife;
            g.setColor(new Color(p.color.getRed(),p.color.getGreen(),p.color.getBlue(),(int)(alpha*255)));
            g.fillOval((int)(p.x-cx)-3,(int)p.y-3,6,6);
        }

        if(state == State.PLAYING || state == State.PAUSED)
            drawPlayerChar(g, (int)(px-cx), (int)py, facingRight, onGround ? (tick/6)%4 : 1);

        drawHUD(g);

        if(currentLevel == 0 && activeTutCard != null) drawTutorialCard(g, activeTutCard);
    }

    void drawPauseMenu(Graphics2D g) {
        g.setColor(new Color(0,0,0,170));
        g.fillRect(0,0,W,H);
        if(pauseLevelSelect) { drawPauseLevelSelect(g); return; }
        int pw=420, ph=340, panX=W/2-pw/2, panY=H/2-ph/2;
        g.setColor(new Color(0,0,0,100));
        g.fillRoundRect(panX+6,panY+6,pw,ph,20,20);
        g.setPaint(new GradientPaint(panX,panY,new Color(12,8,28,245),panX,panY+ph,new Color(22,12,45,245)));
        g.fillRoundRect(panX,panY,pw,ph,20,20);
        g.setColor(new Color(120,80,200,200));
        g.setStroke(new BasicStroke(2f));
        g.drawRoundRect(panX,panY,pw,ph,20,20);
        g.setStroke(new BasicStroke(1f));
        g.setColor(new Color(150,80,255,200));
        g.fillRoundRect(panX,panY+12,5,ph-24,4,4);
        g.setFont(new Font("Courier New", Font.BOLD, 28));
        String title = "II  PAUSED";
        drawShadowText(g, title, W/2-g.getFontMetrics().stringWidth(title)/2, panY+44,
            new Color(180,130,255), new Color(60,20,100));
        g.setColor(new Color(120,80,200,80));
        g.fillRect(panX+20,panY+54,pw-40,1);
        int optStartY=panY+85, optSpacing=44;
        for(int i = 0; i < PAUSE_OPTIONS.length; i++) {
            boolean sel = (pauseSel == i);
            int oy = optStartY + i*optSpacing;
            if(sel) {
                g.setColor(new Color(120,80,200,50));
                g.fillRoundRect(panX+14,oy-22,pw-28,34,8,8);
                g.setColor(new Color(150,100,255,130));
                g.setStroke(new BasicStroke(1.5f));
                g.drawRoundRect(panX+14,oy-22,pw-28,34,8,8);
                g.setStroke(new BasicStroke(1f));
                g.setColor(new Color(255,200,80));
                g.setFont(new Font("Courier New", Font.BOLD, 14));
                g.drawString("›", panX+22, oy+2);
            }
            if(i == 3) {
                g.setFont(new Font("Courier New", Font.BOLD, sel ? 18 : 16));
                g.setColor(sel ? new Color(255,200,80) : new Color(160,140,200));
                g.drawString(PAUSE_OPTIONS[i], panX+40, oy+2);
                int barX=panX+190, barY=oy-10, barW=180, barH=10;
                g.setColor(new Color(60,40,100));
                g.fillRoundRect(barX,barY,barW,barH,5,5);
                int fillW = (int)(barW*(volume/100.0));
                g.setColor(sel ? new Color(200,140,255) : new Color(120,80,180));
                g.fillRoundRect(barX,barY,fillW,barH,5,5);
                g.setColor(sel ? new Color(255,220,100) : new Color(200,170,255));
                g.fillOval(barX+fillW-7,barY-3,14,14);
                g.setFont(new Font("Courier New", Font.PLAIN, 11));
                g.setColor(new Color(200,180,230));
                g.drawString(volume+"%", barX+barW+6, barY+9);
                if(sel) {
                    g.setFont(new Font("Courier New", Font.PLAIN, 10));
                    g.setColor(new Color(180,160,220,180));
                    g.drawString("← / → to adjust", barX, barY+24);
                }
            } else {
                g.setFont(new Font("Courier New", Font.BOLD, sel ? 18 : 16));
                g.setColor(sel ? new Color(255,200,80) : new Color(160,140,200));
                g.drawString(PAUSE_OPTIONS[i], panX+40, oy+2);
            }
        }
        g.setFont(new Font("Courier New", Font.PLAIN, 11));
        g.setColor(new Color(140,120,180,180));
        String hint = "↑ ↓ : Navigate   ENTER / SPACE : Select   ESC : Resume";
        g.drawString(hint, W/2-g.getFontMetrics().stringWidth(hint)/2, panY+ph-12);
    }

    void drawPauseLevelSelect(Graphics2D g) {
        int pw=500, ph=280, panX=W/2-pw/2, panY=H/2-ph/2;
        g.setColor(new Color(0,0,0,100));
        g.fillRoundRect(panX+6,panY+6,pw,ph,20,20);
        g.setPaint(new GradientPaint(panX,panY,new Color(12,18,35,245),panX,panY+ph,new Color(18,28,50,245)));
        g.fillRoundRect(panX,panY,pw,ph,20,20);
        g.setColor(new Color(80,150,200,200));
        g.setStroke(new BasicStroke(2f));
        g.drawRoundRect(panX,panY,pw,ph,20,20);
        g.setStroke(new BasicStroke(1f));
        g.setFont(new Font("Courier New", Font.BOLD, 22));
        String title = "⊞  SELECT LEVEL";
        drawShadowText(g, title, W/2-g.getFontMetrics().stringWidth(title)/2, panY+36,
            new Color(100,200,255), new Color(30,80,120));
        int btnW=80, btnH=90, spacing=12;
        int totalBtnW = 5*btnW + 4*spacing;
        int startX = panX + pw/2 - totalBtnW/2;
        int btnY = panY + 60;
        for(int i = 0; i < 5; i++) {
            int bx = startX + i*(btnW+spacing);
            boolean selected = (pauseLevelSel == i);
            boolean unlocked = (i <= highestUnlockedLevel);
            g.setColor(unlocked ? (selected ? new Color(50,80,130,230) : new Color(30,50,80,200)) : new Color(40,40,55,180));
            g.fillRoundRect(bx,btnY,btnW,btnH,10,10);
            if(selected && unlocked) { g.setColor(new Color(255,200,80,220)); g.setStroke(new BasicStroke(2.5f)); }
            else if(unlocked) { g.setColor(new Color(100,160,220,150)); g.setStroke(new BasicStroke(1.5f)); }
            else { g.setColor(new Color(70,70,90,150)); g.setStroke(new BasicStroke(1f)); }
            g.drawRoundRect(bx,btnY,btnW,btnH,10,10);
            g.setStroke(new BasicStroke(1f));
            g.setFont(new Font("Courier New", Font.BOLD, 32));
            String num = String.valueOf(i+1);
            int numW = g.getFontMetrics().stringWidth(num);
            g.setColor(unlocked ? (selected ? new Color(255,220,100) : new Color(180,210,255)) : new Color(90,90,110));
            g.drawString(num, bx+btnW/2-numW/2, btnY+40);
            g.setFont(new Font("Courier New", Font.PLAIN, 9));
            String name = LEVEL_NAMES[i];
            int nameW = g.getFontMetrics().stringWidth(name);
            g.setColor(unlocked ? (selected ? new Color(255,240,200) : new Color(150,180,220)) : new Color(80,80,100));
            g.drawString(name, bx+btnW/2-nameW/2, btnY+58);
            if(!unlocked) {
                g.setFont(new Font("Courier New", Font.BOLD, 18));
                g.setColor(new Color(100,80,120));
                g.drawString("🔒", bx+btnW/2-10, btnY+80);
            } else if(i < highestUnlockedLevel) {
                g.setFont(new Font("Courier New", Font.BOLD, 14));
                g.setColor(new Color(100,255,150));
                g.drawString("✓", bx+btnW/2-5, btnY+78);
            }
            if(i == currentLevel && unlocked) {
                g.setFont(new Font("Courier New", Font.PLAIN, 8));
                g.setColor(new Color(255,180,80));
                String curr = "CURRENT";
                int currW = g.getFontMetrics().stringWidth(curr);
                g.drawString(curr, bx+btnW/2-currW/2, btnY+btnH+12);
            }
        }
        g.setFont(new Font("Courier New", Font.PLAIN, 12));
        g.setColor(new Color(160,180,220));
        String inst = "← →  Select   |   ENTER  Start   |   ESC  Back";
        g.drawString(inst, W/2-g.getFontMetrics().stringWidth(inst)/2, panY+ph-40);
        if(pauseLevelSel > highestUnlockedLevel) {
            g.setFont(new Font("Courier New", Font.ITALIC, 11));
            g.setColor(new Color(255,150,100));
            String lockHint = "Complete previous levels to unlock!";
            g.drawString(lockHint, W/2-g.getFontMetrics().stringWidth(lockHint)/2, panY+ph-18);
        }
    }

    void drawTutorialZoneMarkers(Graphics2D g, int cx) {
        String[][] zones = {
            {"50","ZONE 1: MOVEMENT"},{"500","ZONE 2: GAPS"},
            {"1220","ZONE 3: SPIKES"},{"1600","ZONE 4: FAKE PLATFORMS"},
            {"1960","ZONE 5: BOUNCY"},{"2320","ZONE 6: MOVING"},
            {"2900","ZONE 7: INVISIBLE"},{"3180","ZONE 8: CANNONS"},
        };
        g.setFont(new Font("Courier New", Font.BOLD, 10));
        for(String[] z : zones) {
            int wx = Integer.parseInt(z[0]);
            int sx = wx - cx;
            if(sx < -100 || sx > W + 100) continue;
            g.setColor(new Color(255,255,255,22));
            g.drawString(z[1], sx, H-60);
        }
    }

    void drawTutorialCard(Graphics2D g, TutorialCard card) {
        int progress = card.displayTimer;
        int total = TutorialCard.DISPLAY_TICKS;
        float alpha = 1f;
        if(progress < 20) alpha = progress / 20f;
        else if(progress > total - 30) alpha = (total - progress) / 30f;
        alpha = Math.max(0, Math.min(1, alpha));
        int cardW=390, lineH=22;
        int numLines = card.lines.length;
        int cardH = 28 + 26 + numLines*lineH + 14;
        int cardX = W/2-cardW/2, cardY = 22;
        g.setColor(new Color(0,0,0,(int)(alpha*120)));
        g.fillRoundRect(cardX+4,cardY+4,cardW,cardH,14,14);
        g.setColor(new Color(8,6,20,(int)(alpha*230)));
        g.fillRoundRect(cardX,cardY,cardW,cardH,14,14);
        Color ac = card.accentColor;
        g.setColor(new Color(ac.getRed(),ac.getGreen(),ac.getBlue(),(int)(alpha*200)));
        g.setStroke(new BasicStroke(2.2f));
        g.drawRoundRect(cardX,cardY,cardW,cardH,14,14);
        g.setStroke(new BasicStroke(1f));
        g.setColor(new Color(ac.getRed(),ac.getGreen(),ac.getBlue(),(int)(alpha*180)));
        g.fillRoundRect(cardX,cardY,6,cardH,6,6);
        g.setColor(new Color(ac.getRed()/5,ac.getGreen()/5,ac.getBlue()/5,(int)(alpha*180)));
        g.fillRoundRect(cardX+1,cardY+1,cardW-2,26,12,12);
        g.setFont(new Font("Courier New", Font.BOLD, 14));
        g.setColor(new Color(ac.getRed(),ac.getGreen(),ac.getBlue(),(int)(alpha*255)));
        g.drawString("▸  " + card.title, cardX+18, cardY+18);
        int barW = cardW - 20;
        float pct = 1f - (float)progress / total;
        g.setColor(new Color(ac.getRed(),ac.getGreen(),ac.getBlue(),(int)(alpha*40)));
        g.fillRoundRect(cardX+10,cardY+26,barW,4,2,2);
        g.setColor(new Color(ac.getRed(),ac.getGreen(),ac.getBlue(),(int)(alpha*160)));
        g.fillRoundRect(cardX+10,cardY+26,(int)(barW*pct),4,2,2);
        g.setFont(new Font("Courier New", Font.PLAIN, 13));
        for(int i = 0; i < card.lines.length; i++) {
            float lineAlpha = alpha * Math.min(1f, (progress - i*8) / 15f);
            lineAlpha = Math.max(0, lineAlpha);
            g.setColor(new Color(220,220,240,(int)(lineAlpha*255)));
            g.drawString(card.lines[i], cardX+18, cardY+50+i*lineH);
        }
    }

    void drawPlayerChar(Graphics2D g, int x, int y, boolean right, int frame) {
        int legSwing = (frame % 2 == 0) ? 4 : -4;
        boolean moving = Math.abs(pvx) > 0.5f;
        g.setColor(new Color(0,0,0,50));
        g.fillOval(x-2, y+playerH+1, playerW+4, 5);
        int leg1Off = moving ? legSwing : 0;
        int leg2Off = moving ? -legSwing : 0;
        g.setColor(new Color(50,50,90));
        g.fillRoundRect(x+1, y+playerH-10+leg1Off/2, 5, 10, 3, 3);
        g.setColor(new Color(80,70,130));
        g.fillRoundRect(x-1, y+playerH-2+leg1Off/2, 8, 4, 2, 2);
        g.setColor(new Color(50,50,90));
        g.fillRoundRect(x+8, y+playerH-10-leg2Off/2, 5, 10, 3, 3);
        g.setColor(new Color(80,70,130));
        g.fillRoundRect(x+6, y+playerH-2-leg2Off/2, 8, 4, 2, 2);
        g.setColor(new Color(200,205,250));
        g.fillRoundRect(x+3, y+11, 8, playerH-21, 4, 4);
        g.setColor(new Color(240,240,255,160));
        g.fillRoundRect(x+4, y+13, 3, playerH-25, 2, 2);
        g.setColor(new Color(140,140,190,180));
        g.fillRoundRect(x+8, y+13, 2, playerH-25, 1, 1);
        g.setColor(new Color(100,100,180));
        g.fillRect(x+3, y+playerH-18, 8, 2);
        int armSwing = moving ? legSwing/2 : 0;
        g.setColor(new Color(180,185,235));
        if(right) {
            g.fillRoundRect(x-4, y+14+armSwing, 5, 3, 2, 2);
            g.fillRoundRect(x+playerW-1, y+14-armSwing, 5, 3, 2, 2);
            g.setColor(new Color(220,215,255));
            g.fillOval(x-5, y+12+armSwing, 5, 5);
            g.fillOval(x+playerW, y+12-armSwing, 5, 5);
        } else {
            g.fillRoundRect(x-4, y+14-armSwing, 5, 3, 2, 2);
            g.fillRoundRect(x+playerW-1, y+14+armSwing, 5, 3, 2, 2);
            g.setColor(new Color(220,215,255));
            g.fillOval(x-5, y+12-armSwing, 5, 5);
            g.fillOval(x+playerW, y+12+armSwing, 5, 5);
        }
        float bob = (float)Math.sin(dotBobTick * 0.09f) * 2.2f;
        if(!onGround) bob = (float)Math.sin(dotBobTick * 0.05f) * 1.2f;
        int dotX = x+3, dotY = (int)(y-3+bob);
        g.setColor(new Color(180,130,255,60));
        g.fillOval(dotX-5, dotY-5, 18, 18);
        g.setColor(new Color(210,170,255,90));
        g.fillOval(dotX-3, dotY-3, 14, 14);
        g.setColor(new Color(235,210,255));
        g.fillOval(dotX, dotY, 8, 8);
        g.setColor(new Color(255,255,255,200));
        g.fillOval(dotX+1, dotY+1, 4, 3);
        g.setColor(new Color(40,20,70));
        if(right) g.fillOval(dotX+5, dotY+3, 2, 2);
        else       g.fillOval(dotX+1, dotY+3, 2, 2);
        g.setColor(new Color(60,30,90));
        if(Math.abs(pvy) > 8f)      g.drawOval(dotX+2, dotY+5, 3, 3);
        else if(!onGround)          g.drawArc(dotX+1, dotY+3, 6, 4, 0, -180);
        else if(Math.abs(pvx) > 3f) g.drawLine(dotX+1, dotY+6, dotX+6, dotY+6);
        else                        g.drawArc(dotX+1, dotY+3, 5, 4, 0, 180);
        if(Math.abs(pvx) > 4f && onGround) {
            int lineDir = pvx > 0 ? -1 : 1;
            g.setColor(new Color(200,200,255,60));
            for(int li = 0; li < 3; li++) {
                int lx = x + playerW/2 + lineDir*(8+li*5);
                int ly = y + 10 + li*8;
                g.drawLine(lx, ly, lx+lineDir*8, ly);
            }
        }
    }

    void drawCannon(Graphics2D g, int x, int y, boolean right) {
        g.setColor(new Color(55,35,15));
        g.fillRect(x-5, y+22, 40, 18);
        g.setColor(new Color(80,55,25));
        g.fillRect(x-5, y+22, 40, 3);
        for(int wx = 0; wx < 2; wx++) {
            int wbx = x-2+wx*20, wby = y+28;
            g.setColor(new Color(35,22,8));
            g.fillOval(wbx, wby, 16, 16);
            g.setColor(new Color(65,45,20));
            g.setStroke(new BasicStroke(2f));
            g.drawOval(wbx+1, wby+1, 14, 14);
            g.setStroke(new BasicStroke(1f));
            int wcx=wbx+8, wcy=wby+8;
            g.setColor(new Color(90,65,30));
            for(int si = 0; si < 4; si++) {
                double a = si*Math.PI/4 + (tick*0.05f)*(right?1:-1);
                g.drawLine(wcx, wcy, wcx+(int)(6*Math.cos(a)), wcy+(int)(6*Math.sin(a)));
            }
            g.setColor(new Color(120,90,40));
            g.fillOval(wcx-3, wcy-3, 6, 6);
        }
        int bx = right ? x+18 : x-12;
        g.setColor(new Color(60,62,72));
        g.fillRoundRect(bx, y+8, 32, 16, 7, 7);
        g.setColor(new Color(100,102,120));
        g.fillRoundRect(bx, y+8, 32, 5, 7, 4);
        g.setColor(new Color(40,40,50));
        g.fillRect(right ? bx+8 : bx+20, y+8, 4, 16);
        int muzzleX = right ? bx+26 : bx;
        g.setColor(new Color(20,20,28));
        g.fillOval(muzzleX, y+10, 8, 12);
        g.setColor(new Color(70,70,85));
        g.drawOval(muzzleX, y+10, 8, 12);
        if((tick/4) % 3 == 0) {
            float pulse = 0.5f + 0.5f*(float)Math.sin(tick*0.3f);
            g.setColor(new Color(255,180,0,(int)(100+60*pulse)));
            int fx = right ? x+48 : x-16;
            g.fillOval(fx, y+6, 18, 18);
            g.setColor(new Color(255,255,150,(int)(80*pulse)));
            g.fillOval(fx+3, y+9, 10, 10);
        }
    }

    void drawGoal(Graphics2D g, int x, int y) {
        float pulse = 0.5f + 0.5f*(float)Math.sin(tick*0.07f);
        float spin = tick * 0.04f;
        for(int ri = 3; ri >= 1; ri--) {
            int r = ri*14;
            int alpha = (int)(15+12*pulse)*(4-ri);
            g.setColor(new Color(60,210,100,alpha));
            g.fillOval(x-r+GOAL_W/2, y-r+GOAL_H/2, GOAL_W+r*2, GOAL_H+r*2);
        }
        g.setColor(new Color(10,80,30));
        g.fillOval(x, y, GOAL_W, GOAL_H);
        for(int i = 0; i < 6; i++) {
            double a = spin + i*Math.PI/3.0;
            int ix = x+GOAL_W/2+(int)(14*Math.cos(a));
            int iy = y+GOAL_H/2+(int)(18*Math.sin(a));
            int alpha2 = (int)(40+30*Math.sin(spin*3+i));
            g.setColor(new Color(50,200,100,alpha2));
            g.fillOval(ix-6, iy-6, 12, 12);
        }
        g.setColor(new Color(50,200,100,(int)(100+60*pulse)));
        g.fillOval(x+6, y+8, GOAL_W-12, GOAL_H-16);
        g.setColor(new Color(150,255,180,(int)(80+60*pulse)));
        g.fillOval(x+10, y+14, GOAL_W-20, GOAL_H-26);
        g.setColor(new Color(0,210,90));
        g.setStroke(new BasicStroke(3f));
        g.drawOval(x, y, GOAL_W, GOAL_H);
        g.setColor(new Color(100,255,160,(int)(160+60*pulse)));
        g.setStroke(new BasicStroke(1.5f));
        g.drawOval(x+4, y+4, GOAL_W-8, GOAL_H-8);
        g.setStroke(new BasicStroke(1f));
        for(int i = 0; i < 5; i++) {
            double sa = spin*2 + i*Math.PI*0.4;
            int spx = x+GOAL_W/2+(int)(22*Math.cos(sa));
            int spy = y+GOAL_H/2+(int)(28*Math.sin(sa));
            int salpha = (int)(80+60*Math.sin(spin*4+i*1.2));
            g.setColor(new Color(180,255,200,salpha));
            g.fillOval(spx-2, spy-2, 4, 4);
        }
        g.setColor(Color.WHITE);
        g.setFont(new Font("Courier New", Font.BOLD, 9));
        g.drawString("EXIT", x+5, y+GOAL_H/2+4);
    }

    void drawHUD(Graphics2D g) {
        g.setColor(new Color(0,0,0,150)); g.fillRoundRect(8,8,220,72,10,10);
        g.setFont(new Font("Courier New", Font.BOLD, 13));
        g.setColor(new Color(100,220,255));
        g.drawString("LEVEL: "+(currentLevel+1)+"/5 — "+getLevelName(), 16, 27);
        g.setColor(new Color(255,220,80));
        g.drawString("SCORE: "+totalScore, 16, 45);
        g.setColor(new Color(255,100,100));
        g.drawString("DEATHS: "+deaths, 16, 63);
        g.setFont(new Font("Courier New", Font.PLAIN, 11));
        g.setColor(new Color(255,255,255,70));
        String pauseHint = "ESC: Pause";
        g.drawString(pauseHint, W-g.getFontMetrics().stringWidth(pauseHint)-10, 20);
        g.setFont(new Font("Courier New", Font.PLAIN, 11));
        g.setColor(new Color(255,255,255,90));
        g.drawString("A/D: Move   SPACE: Jump   R: Restart   ESC: Pause", W/2-190, H-10);
    }

    String getLevelName() {
        return switch(currentLevel) {
            case 0 -> "Tutorial"; case 1 -> "Getting There"; case 2 -> "Halfway Through";
            case 3 -> "Almost There"; case 4 -> "The Finale"; default -> "???";
        };
    }

    void drawDeathScreen(Graphics2D g) {
        g.setColor(new Color(0,0,0,160)); g.fillRect(0,0,W,H);
        int pw=500, ph=220, panX=W/2-pw/2, panY=H/2-ph/2;
        g.setColor(new Color(15,5,25,230)); g.fillRoundRect(panX,panY,pw,ph,18,18);
        g.setColor(new Color(200,30,30)); g.setStroke(new BasicStroke(2));
        g.drawRoundRect(panX,panY,pw,ph,18,18); g.setStroke(new BasicStroke(1));
        g.setFont(new Font("Courier New", Font.BOLD, 42));
        drawShadowText(g, currentDeathMsg[2],
            W/2-g.getFontMetrics().stringWidth(currentDeathMsg[2])/2, panY+55,
            new Color(255,60,60), new Color(100,0,0));
        g.setFont(new Font("Courier New", Font.BOLD, 20));
        g.setColor(new Color(240,220,220));
        String msg = currentDeathMsg[0];
        g.drawString(msg, W/2-g.getFontMetrics().stringWidth(msg)/2, panY+100);
        g.setFont(new Font("Courier New", Font.ITALIC, 15));
        g.setColor(new Color(180,160,180));
        String sub = currentDeathMsg[1];
        g.drawString(sub, W/2-g.getFontMetrics().stringWidth(sub)/2, panY+125);
        g.setFont(new Font("Courier New", Font.PLAIN, 13));
        g.setColor(new Color(255,120,120));
        String dc = "Total deaths: "+deaths+" — you got this!";
        g.drawString(dc, W/2-g.getFontMetrics().stringWidth(dc)/2, panY+155);
        if((tick/20)%2 == 0) {
            g.setFont(new Font("Courier New", Font.BOLD, 15));
            g.setColor(new Color(255,200,80));
            String pr = "R: Try Again   |   ESC: Menu";
            g.drawString(pr, W/2-g.getFontMetrics().stringWidth(pr)/2, panY+193);
        }
    }

    void drawWinLevel(Graphics2D g) {
        g.setColor(new Color(0,0,0,120)); g.fillRect(0,0,W,H);
        int pw=440, ph=160, panX=W/2-pw/2, panY=H/2-ph/2-30;
        g.setColor(new Color(5,25,15,220)); g.fillRoundRect(panX,panY,pw,ph,15,15);
        g.setColor(new Color(0,200,80)); g.setStroke(new BasicStroke(2));
        g.drawRoundRect(panX,panY,pw,ph,15,15); g.setStroke(new BasicStroke(1));
        g.setFont(new Font("Courier New", Font.BOLD, 34));
        String msg = (currentLevel >= 4) ? "YOU DID IT!!!" : "LEVEL CLEAR!";
        drawShadowText(g, msg, W/2-g.getFontMetrics().stringWidth(msg)/2, panY+50,
            new Color(80,255,130), new Color(0,80,40));
        g.setFont(new Font("Courier New", Font.PLAIN, 15));
        g.setColor(new Color(180,240,200));
        String sub = (currentLevel < 4) ? "Next level incoming... (stay sharp!)" : "You beat it. Genuinely impressive.";
        g.drawString(sub, W/2-g.getFontMetrics().stringWidth(sub)/2, panY+83);
        g.setColor(new Color(0,80,40));
        g.fillRoundRect(panX+20, panY+103, pw-40, 18, 6, 6);
        float prog = (float)winTimer / 100;
        g.setColor(new Color(0,200,80));
        g.fillRoundRect(panX+20, panY+103, (int)((pw-40)*prog), 18, 6, 6);
        g.setFont(new Font("Courier New", Font.PLAIN, 11));
        g.setColor(new Color(255,255,255,150));
        g.drawString("Loading next level...", panX+20, panY+116);
    }

    void drawWinGame(Graphics2D g) {
        float t = tick * 0.015f;
        g.setPaint(new GradientPaint(0,0,
            new Color((int)(10+10*Math.sin(t)),30,(int)(10+10*Math.sin(t+2))),
            W,H, new Color(5,(int)(30+15*Math.sin(t+1)),10)));
        g.fillRect(0,0,W,H);
        for(Particle p : particles) {
            float alpha = (float)p.life / p.maxLife;
            g.setColor(new Color(p.color.getRed(),p.color.getGreen(),p.color.getBlue(),(int)(alpha*255)));
            g.fillRect((int)(p.x-camX/4)-3,(int)p.y-3,6,6);
        }
        g.setFont(new Font("Courier New", Font.BOLD, 52));
        drawShadowText(g, "YOU ESCAPED!", W/2-g.getFontMetrics().stringWidth("YOU ESCAPED!")/2, 130,
            new Color(100,255,140), new Color(0,100,50));
        g.setFont(new Font("Courier New", Font.ITALIC, 18));
        String[] lines = {
            "Against all odds. Against...whatever that was.",
            "You, a tiny letter 'i', survived 5 levels.",
            "You didn't give up here, so don't give up there!",
            "",
            "Final Score: " + totalScore,
            "Total Deaths: " + deaths + "  (" + getRank() + ")"
        };
        for(int i = 0; i < lines.length; i++) {
            g.setColor(lines[i].startsWith("Final") || lines[i].startsWith("Total")
                ? new Color(255,230,80) : new Color(180,240,195));
            g.drawString(lines[i], W/2-g.getFontMetrics().stringWidth(lines[i])/2, 195+i*32);
        }
        if((tick/25)%2 == 0) {
            g.setFont(new Font("Courier New", Font.BOLD, 16));
            g.setColor(new Color(255,200,80));
            String pr = "ESC: Main Menu";
            g.drawString(pr, W/2-g.getFontMetrics().stringWidth(pr)/2, H-30);
        }
        if(tick%3==0 && particles.size()<200)
            particles.add(new Particle(rng.nextInt(W),-10,
                (rng.nextFloat()-0.5f)*2, 2+rng.nextFloat()*3,
                120+rng.nextInt(60), new Color(rng.nextInt(255),rng.nextInt(255),rng.nextInt(255))));
        Iterator<Particle> it = particles.iterator();
        while(it.hasNext()) { Particle p=it.next(); p.x+=p.vx; p.y+=p.vy; p.life--; if(p.life<=0) it.remove(); }
    }

    String getRank() {
        if(deaths==0)       return "Impossible. Are you human?";
        else if(deaths<5)   return "Suspiciously good";
        else if(deaths<15)  return "Genuinely impressive";
        else if(deaths<30)  return "Solid run";
        else if(deaths<60)  return "Getting there";
        else if(deaths<100) return "Determination";
        else                return "Never give up!";
    }

    void drawShadowText(Graphics2D g, String text, int x, int y, Color main, Color shadow) {
        g.setColor(shadow); g.drawString(text, x+3, y+3);
        g.setColor(main);   g.drawString(text, x, y);
    }

    @Override
    public void keyPressed(KeyEvent e) {
        int k = e.getKeyCode();
        if(state == State.MENU) {
            if(k==KeyEvent.VK_UP   || k==KeyEvent.VK_W) menuSel = Math.max(0, menuSel-1);
            if(k==KeyEvent.VK_DOWN || k==KeyEvent.VK_S) menuSel = Math.min(2, menuSel+1);
            if(k==KeyEvent.VK_ENTER || k==KeyEvent.VK_SPACE) {
                switch(menuSel) {
                    case 0 -> { currentLevel=0; totalScore=0; deaths=0; beginWipeToLevel(0); }
                    case 1 -> { levelSelectSel=0; state=State.LEVEL_SELECT; }
                    case 2 -> System.exit(0);
                }
            }
            return;
        }
        if(state == State.LEVEL_SELECT) {
            if(k==KeyEvent.VK_LEFT  || k==KeyEvent.VK_A) levelSelectSel = Math.max(0, levelSelectSel-1);
            if(k==KeyEvent.VK_RIGHT || k==KeyEvent.VK_D) levelSelectSel = Math.min(4, levelSelectSel+1);
            if(k==KeyEvent.VK_ENTER || k==KeyEvent.VK_SPACE) {
                if(levelSelectSel <= highestUnlockedLevel) {
                    currentLevel=levelSelectSel; totalScore=0; deaths=0;
                    beginWipeToLevel(currentLevel);
                }
            }
            if(k==KeyEvent.VK_ESCAPE) state=State.MENU;
            return;
        }
        if(state == State.TRANSITIONING) return;
        if(state == State.WIN_GAME) {
            if(k==KeyEvent.VK_ESCAPE) { state=State.MENU; particles.clear(); }
            return;
        }
        if(state == State.PAUSED) { handlePauseKey(k); return; }
        if(state == State.DYING) return;
        if(k==KeyEvent.VK_A     || k==KeyEvent.VK_LEFT)  leftDown = true;
        if(k==KeyEvent.VK_D     || k==KeyEvent.VK_RIGHT) rightDown = true;
        if(k==KeyEvent.VK_SPACE || k==KeyEvent.VK_UP || k==KeyEvent.VK_W) jumpDown = true;
        if(k==KeyEvent.VK_R) {
            if(state==State.DEAD || state==State.PLAYING) startLevel(currentLevel);
        }
        if(k==KeyEvent.VK_ESCAPE) {
            if(state==State.PLAYING) {
                leftDown=false; rightDown=false; jumpDown=false;
                pauseSel=0; pauseLevelSelect=false; state=State.PAUSED;
            } else if(state==State.DEAD) {
                state=State.MENU; particles.clear(); leftDown=rightDown=jumpDown=false;
            }
        }
    }

    void handlePauseKey(int k) {
        if(pauseLevelSelect) {
            if(k==KeyEvent.VK_LEFT  || k==KeyEvent.VK_A) pauseLevelSel = Math.max(0, pauseLevelSel-1);
            if(k==KeyEvent.VK_RIGHT || k==KeyEvent.VK_D) pauseLevelSel = Math.min(4, pauseLevelSel+1);
            if(k==KeyEvent.VK_ENTER || k==KeyEvent.VK_SPACE) {
                if(pauseLevelSel <= highestUnlockedLevel) {
                    currentLevel=pauseLevelSel; pauseLevelSelect=false;
                    beginWipeToLevel(currentLevel);
                }
            }
            if(k==KeyEvent.VK_ESCAPE) pauseLevelSelect=false;
            return;
        }
        if(pauseSel==3) {
            if(k==KeyEvent.VK_LEFT)  { volume=Math.max(0,volume-5);   applyVolume(); return; }
            if(k==KeyEvent.VK_RIGHT) { volume=Math.min(100,volume+5); applyVolume(); return; }
        }
        if(k==KeyEvent.VK_UP   || k==KeyEvent.VK_W)
            pauseSel = (pauseSel-1+PAUSE_OPTIONS.length) % PAUSE_OPTIONS.length;
        if(k==KeyEvent.VK_DOWN || k==KeyEvent.VK_S)
            pauseSel = (pauseSel+1) % PAUSE_OPTIONS.length;
        if(k==KeyEvent.VK_ENTER || k==KeyEvent.VK_SPACE) {
            switch(pauseSel) {
                case 0 -> state=State.PLAYING;
                case 1 -> startLevel(currentLevel);
                case 2 -> { pauseLevelSel=currentLevel; pauseLevelSelect=true; }
                case 3 -> { volume=Math.min(100,volume+10); if(volume>100) volume=0; applyVolume(); }
                case 4 -> { state=State.MENU; particles.clear(); leftDown=false; rightDown=false; jumpDown=false; }
            }
        }
        if(k==KeyEvent.VK_ESCAPE) state=State.PLAYING;
    }

    void applyVolume() { /* wire up to Clip FloatControl */ }

    @Override
    public void keyReleased(KeyEvent e) {
        int k = e.getKeyCode();
        if(k==KeyEvent.VK_A     || k==KeyEvent.VK_LEFT)  leftDown=false;
        if(k==KeyEvent.VK_D     || k==KeyEvent.VK_RIGHT) rightDown=false;
        if(k==KeyEvent.VK_SPACE || k==KeyEvent.VK_UP || k==KeyEvent.VK_W) jumpDown=false;
    }

    @Override public void keyTyped(KeyEvent e) {}

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