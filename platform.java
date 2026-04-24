import java.awt.*;
import java.awt.event.*;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.Random;
import javax.swing.*;

public class platform extends JPanel implements ActionListener, KeyListener {

    // ── Window ──────────────────────────────────────────────
    static final int W = 900, H = 550;

    // ── Physics (smoother, more forgiving) ──────────────────
    static final float GRAVITY      = 0.55f;
    static final float JUMP_VEL     = -14.5f;
    static final float MOVE_SPD     = 5.5f;
    static final float ACCEL        = 0.85f;
    static final float FRICTION     = 0.78f;
    static final float AIR_FRICTION = 0.92f;
    static final float MAX_FALL     = 14f;
    static final int   COYOTE_TIME  = 8;
    static final int   JUMP_BUFFER  = 8;

    // ── Game States ─────────────────────────────────────────
    enum State { MENU, LEVEL_SELECT, PLAYING, PAUSED, DEAD, WIN_LEVEL, WIN_GAME }
    State state = State.MENU;

    int currentLevel = 0;
    int totalScore   = 0;
    int deaths       = 0;
    int highestUnlockedLevel = 0; // Track progression

    // ── Player ───────────────────────────────────────────────
    float px = 80, py = 300, pvx = 0, pvy = 0;
    boolean onGround    = false;
    boolean facingRight = true;
    int playerW = 14, playerH = 36;
    int dotBobTick = 0;
    int coyoteTimer   = 0;
    int jumpBufferTimer = 0;
    boolean wasOnGround = false;

    // ── Input ────────────────────────────────────────────────
    boolean leftDown, rightDown, jumpDown, jumpConsumed;

    // ── Camera ───────────────────────────────────────────────
    float camX = 0;

    // ── Menu Selection ───────────────────────────────────────
    int menuSel = 0;
    int levelSelectSel = 0;
    static final String[] MENU_OPTIONS = { "▶  START GAME", "⊞  LEVEL SELECT", "✕  QUIT" };
    static final String[] LEVEL_NAMES = { "Tutorial", "Getting There", "Troll Central", "Almost There", "The Finale" };

    // ── Pause Menu ───────────────────────────────────────────
    int pauseSel = 0;
    boolean pauseLevelSelect = false; // Sub-menu toggle
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

    // ── Holes ────────────────────────────────────────────────
    static class AppearingHole {
        int platformIndex;
        int holeX, holeW;
        int revealDelay;
        boolean active = false;
        boolean triggered = false;
        int warningFlash = 0;
        AppearingHole(int pi, int hx, int hw, int delay){
            platformIndex=pi; holeX=hx; holeW=hw; revealDelay=delay;
        }
    }
    ArrayList<AppearingHole> holes = new ArrayList<>();
    int holePlayerTick = 0;

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
    int levelTimer = 0;
    int hintIndex  = 0;
    boolean hintVisible = false;
    int hintFadeTimer = 0;
    static final int HINT_DELAY = 60 * 20;

    static final String[][] LEVEL_HINTS = {
        { "Hint 1: Look before you leap — gaps aren't always deadly.",
        "Hint 2: Purple blinking platforms are fake! They'll fall!",
        "Hint 3: Cyan platforms shoot you upward — use them!",
        "Hint 4: Cannons fire in a rhythm. Wait for the gap.",
        "Hint 5: The EXIT portal is at the far right. Almost there!" },
        { "Hint 1: Moving platforms have a steady rhythm — time your jump.",
        "Hint 2: Fake platforms blink. Real ones are solid color.",
        "Hint 3: Spikes at platform edges — don't land on the very end!",
        "Hint 4: Bouncy pads launch you high. Aim for the upper ledge.",
        "Hint 5: Stay patient. Rushing causes deaths here." },
        { "Hint 1: Some platforms SHIFT sideways when you land. Jump fast!",
        "Hint 2: Invisible platforms exist — look for faint sparkles.",
        "Hint 3: Chasing spikes speed up as you slow down. Keep moving!",
        "Hint 4: Holes appear in platforms after a short warning flash.",
        "Hint 5: The top path is safer but longer. Your call!" },
        { "Hint 1: Every platform gap can be jumped — none require perfect timing.",
        "Hint 2: Yellow warning flicker = hole about to appear. Move!",
        "Hint 3: The chasing spike resets if you fall back to a safe zone.",
        "Hint 4: Bouncy pads are your friends — they skip dangerous sections.",
        "Hint 5: The final stretch has a wide safe platform. Sprint for it!" },
        { "Hint 1: The chasing spike starts far back — keep a steady pace.",
        "Hint 2: Bouncy pads on the upper path let you skip ground hazards.",
        "Hint 3: Invisible platforms are always above a ground platform.",
        "Hint 4: Shift platforms move left or right — jump the moment you land.",
        "Hint 5: The final safe platform is huge. Once you're on it, you've won!" }
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

    // ══════════════════════════════════════════════════════════
    //  LEVEL BUILDING
    // ══════════════════════════════════════════════════════════

    void startLevel(int lvl) {
        platforms.clear(); spikes.clear(); cannons.clear();
        cannonballs.clear(); particles.clear(); holes.clear();
        tutCards.clear(); activeTutCard = null;
        px=60; py=400; pvx=0; pvy=0; camX=0;
        onGround=false; jumpConsumed=false; wasOnGround=false;
        coyoteTimer=0; jumpBufferTimer=0;
        winTimer=0; levelTimer=0; hintIndex=0;
        hintVisible=false; hintFadeTimer=0;
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

    // ── LEVEL 1: Tutorial ────────────────────────────────────
void buildLevel1() {
    levelW = 3400;
    addPlatform(0,    H-50, 500, 50);
    addPlatform(560,  H-50, 300, 50);
    addPlatform(920,  H-50, 300, 50);
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
    m1.moving = true; 
    m1.mx = 2490; 
    m1.mrange = 100; 
    m1.mspeed = 1.3f;
    addPlatform(2720, H-50, 200, 50);
    addPlatform(2980, H-50, 220, 50);
    Platform inv1 = addPlatform(3060, 360, 110, 15); 
    inv1.invisible = true;
    addPlatform(3260, H-50, 240, 50);
    int pIdx = findPlatformAt(3260, H-50);
    if (pIdx >= 0) addHole(pIdx, 80, 35, 200);
    addPlatform(3560, H-50, 250, 50);
    cannons.add(new Cannon(3580, H-90, false, 150));
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
        tutCards.add(new TutorialCard(2820, "INVISIBLE PLATFORMS", new Color(200, 170, 255),
            "Some platforms are near-invisible!",
            "Look for faint sparkles ✦",
            "Step carefully — they're solid."));
        tutCards.add(new TutorialCard(3110, "CANNONS  💥", new Color(255, 120, 60),
            "Cannons fire on a rhythm.",
            "Watch the timing — wait for",
            "the gap, then run through!"));

        addPlatform(3560, H-50, 250, 50);

        cannons.add(new Cannon(3180, H-90, false, 140));

        goalX = 3350; goalY = H-110;
    }

    // ── LEVEL 2 ──────────────────────────────────────────────
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
        addSpikeChasing(200, H-65, 15, 1.2f);
        addSpikes(345, H-65, 2, 15); addSpikes(645, H-65, 2, 15);
        addSpikes(905, H-65, 2, 15); addSpikes(1185,H-65, 2, 15);
        addSpikes(1745,H-65, 2, 15); addSpikes(2025,H-65, 2, 15);
        int p1 = findPlatformAt(900, H-50); if(p1>=0) addHole(p1, 70, 30, 200);
        int p2 = findPlatformAt(1740, H-50); if(p2>=0) addHole(p2, 50, 30, 180);
        cannons.add(new Cannon(640,  H-90, false, 120));
        cannons.add(new Cannon(1470, H-90, false, 100));
        cannons.add(new Cannon(2300, H-90, false, 110));
        goalX = 2800; goalY = H-110;
    }

    // ── LEVEL 3 ──────────────────────────────────────────────
    void buildLevel3() {
        levelW = 3600;
        addPlatform(0,   H-50, 220, 50); addPlatform(290, H-50, 180, 50);
        addPlatform(560, H-50, 160, 50); addPlatform(820, H-50, 180, 50);
        addPlatform(1100,H-50, 160, 50); addPlatform(1360,H-50, 180, 50);
        addPlatform(1640,H-50, 160, 50); addPlatform(1900,H-50, 180, 50);
        addPlatform(2180,H-50, 160, 50); addPlatform(2460,H-50, 180, 50);
        addPlatform(2740,H-50, 160, 50); addPlatform(3020,H-50, 180, 50);
        addPlatform(3300,H-50, 300, 50);
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
        shift2.shiftOnStep=true; shift2.shiftDist=-120;
        Platform b2 = addPlatform(2350, 320, 90, 15); b2.bouncy=true;
        addPlatform(2480, 180, 110, 15);
        Platform m3 = addPlatform(2640, 300, 100, 15);
        m3.moving=true; m3.mx=2640; m3.mrange=160; m3.mspeed=2.5f;
        addPlatform(2900, 260, 110, 15);
        addSpikeChasing(300, H-65, 15, 1.8f);
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

    // ── LEVEL 4 ──────────────────────────────────────────────
    void buildLevel4() {
        levelW = 4000;
        addPlatform(0,   H-50, 180, 50); addPlatform(260, H-50, 160, 50);
        addPlatform(520, H-50, 160, 50); addPlatform(790, H-50, 160, 50);
        addPlatform(1060,H-50, 160, 50); addPlatform(1330,H-50, 160, 50);
        addPlatform(1600,H-50, 160, 50); addPlatform(1870,H-50, 160, 50);
        addPlatform(2150,H-50, 160, 50); addPlatform(2430,H-50, 160, 50);
        addPlatform(2710,H-50, 160, 50); addPlatform(2990,H-50, 160, 50);
        addPlatform(3270,H-50, 160, 50); addPlatform(3550,H-50, 450, 50);
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
        addSpikeChasing(150, H-65, 15, 1.5f);
        addSpikeChasing(2200, H-65, 15, 2.0f);
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

    // ── LEVEL 5 ──────────────────────────────────────────────
    void buildLevel5() {
        levelW = 4200;
        addPlatform(0,    H-50, 260, 50);
        addPlatform(360,  H-50, 200, 50); addPlatform(640,  H-50, 200, 50);
        addPlatform(920,  H-50, 200, 50); addPlatform(1200, H-50, 200, 50);
        addPlatform(1480, H-50, 200, 50); addPlatform(1760, H-50, 200, 50);
        addPlatform(2040, H-50, 200, 50); addPlatform(2320, H-50, 200, 50);
        addPlatform(2600, H-50, 200, 50); addPlatform(2880, H-50, 200, 50);
        addPlatform(3160, H-50, 200, 50); addPlatform(3440, H-50, 200, 50);
        addPlatform(3720, H-50, 480, 50);
        addPlatform(460, H-180, 120, 15);
        Platform b1 = addPlatform(740, H-180, 120, 15); b1.bouncy = true;
        addPlatform(820, H-280, 130, 15);
        Platform fake1 = addPlatform(1020, H-180, 120, 15); fake1.fake = true;
        Platform mov1 = addPlatform(1300, H-180, 110, 15);
        mov1.moving = true; mov1.mx = 1300; mov1.mrange = 110; mov1.mspeed = 1.6f;
        Platform inv1 = addPlatform(1580, H-180, 120, 15); inv1.invisible = true;
        Platform shift1 = addPlatform(1860, H-180, 120, 15);
        shift1.shiftOnStep = true; shift1.shiftDist = 110;
        Platform mov2 = addPlatform(2140, H-180, 110, 15);
        mov2.moving = true; mov2.mx = 2140; mov2.mrange = 120; mov2.mspeed = 2.0f;
        Platform b2 = addPlatform(2420, H-180, 120, 15); b2.bouncy = true;
        addPlatform(2500, H-280, 130, 15);
        Platform inv2 = addPlatform(2700, H-180, 120, 15); inv2.invisible = true;
        Platform shift2 = addPlatform(2980, H-180, 120, 15);
        shift2.shiftOnStep = true; shift2.shiftDist = -110;
        Platform mov3 = addPlatform(3260, H-190, 100, 15);
        mov3.moving = true; mov3.mx = 3260; mov3.mrange = 100; mov3.mspeed = 2.2f;
        addSpikes(370,  H-65, 2, 15); addSpikes(930,  H-65, 2, 15);
        addSpikes(1210, H-65, 2, 15); addSpikes(1770, H-65, 2, 15);
        addSpikes(2050, H-65, 2, 15); addSpikes(2610, H-65, 2, 15);
        addSpikes(2890, H-65, 2, 15); addSpikes(3170, H-65, 2, 15);
        addSpikeChasing(-600, H-65, 15, 1.4f);
        int p7  = findPlatformAt(2040, H-50); if(p7  >= 0) addHole(p7,  70, 35, 180);
        int p9  = findPlatformAt(2600, H-50); if(p9  >= 0) addHole(p9,  60, 35, 160);
        int p11 = findPlatformAt(3160, H-50); if(p11 >= 0) addHole(p11, 65, 35, 170);
        cannons.add(new Cannon(1480, H-90, false, 110));
        cannons.add(new Cannon(2320, H-90, true,  100));
        cannons.add(new Cannon(2880, H-90, false,  95));
        cannons.add(new Cannon(3440, H-90, false,  90));
        goalX = 3900; goalY = H-110;
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
                // Update highest unlocked level
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

    void update() {
        levelTimer++;

        if(currentLevel == 0) updateTutorialCards();

        if(levelTimer % HINT_DELAY == 0 && hintIndex < 5) {
            hintVisible = true; hintFadeTimer = 300;
        }
        if(hintVisible) {
            hintFadeTimer--;
            if(hintFadeTimer <= 0) {
                hintVisible = false;
                hintIndex = Math.min(hintIndex+1, 4);
            }
        }

        if(jumpDown) jumpBufferTimer = JUMP_BUFFER;
        else if(jumpBufferTimer > 0) jumpBufferTimer--;

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

        if(onGround) coyoteTimer = COYOTE_TIME;
        else if(coyoteTimer > 0) coyoteTimer--;

        wasOnGround = onGround;

        boolean canJump = coyoteTimer > 0 && !jumpConsumed;
        if(jumpBufferTimer > 0 && canJump) {
            pvy = JUMP_VEL;
            jumpConsumed = true;
            jumpBufferTimer = 0;
            coyoteTimer = 0;
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
                float proximity = 1f - Math.min(Math.abs(dist)/400f, 0.8f);
                s.cx += dir * s.cspeed * (0.4f + proximity * 1.0f);
            }
            s.x = (int)s.cx;
        }

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
                if(p.shiftTimer <= 20) p.x += p.shiftDist / 20;
            }
            if(p.invisible && p.revealTimer_active) {
                p.revealFlash--;
                if(p.revealFlash <= 0) p.revealTimer_active = false;
            }
        }

        for(AppearingHole h : holes) {
            if(!h.triggered) {
                h.revealDelay--;
                if(h.revealDelay <= 60) h.warningFlash++;
                if(h.revealDelay <= 0) { h.active=true; h.triggered=true; }
            }
        }

        onGround = false;
        Rectangle pr = new Rectangle((int)px, (int)py, playerW, playerH);

        for(int pIdx=0; pIdx<platforms.size(); pIdx++) {
            Platform p = platforms.get(pIdx);
            if(p.fake && p.fakeTimer > 50) continue;
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
            if(pvy >= 0 && py + playerH - pvy <= pl.y + 8) {
                py = pl.y - playerH;
                if(p.bouncy) {
                    pvy = JUMP_VEL * 1.5f;
                    spawnBounceParticles((int)px, (int)py);
                } else {
                    pvy = 0; onGround = true;
                    if(p.fake && !p.fakeTriggered) p.fakeTriggered = true;
                    if(p.shiftOnStep && !p.shiftTriggered) p.shiftTriggered = true;
                    if(p.invisible) { p.revealTimer_active=true; p.revealFlash=45; }
                }
            }
            else if(pvy < 0 && py - pvy >= pl.y + pl.height - 5) {
                py = pl.y + pl.height; pvy = 1;
            }
            else if(pvx > 0) { px = pl.x - playerW; pvx=0; }
            else if(pvx < 0) { px = pl.x + pl.width; pvx=0; }
        }

        if(py > H + 60) { killPlayer("Fell into a pit.", "Didn't jump. Incredible.", "GRAVITY"); return; }

        pr = new Rectangle((int)px+3, (int)py+2, playerW-6, playerH-2);
        for(Spike s : spikes) {
            if(pr.intersects(s.rect())) {
                if(s.chasing) killPlayer("Chasing spike caught up.", "They're faster than they look.", "RUN");
                else          killPlayer("Walked into a spike.", "Happens to everyone.", "OUCH");
                return;
            }
        }

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

        float targetCam = px - W/2f + playerW/2f;
        camX += (targetCam - camX) * 0.10f;
        camX = Math.max(0, Math.min(camX, levelW - W));

        Rectangle goalRect = new Rectangle(goalX, goalY, GOAL_W, GOAL_H);
        if(new Rectangle((int)px,(int)py,playerW,playerH).intersects(goalRect)) {
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
        if(state == State.DEAD) return;
        deaths++;
        // Use custom message OR random pool
        if(l1 != null && l2 != null && tag != null) {
            currentDeathMsg = new String[]{l1, l2, tag};
        } else {
            // no-repeat random selection
            int idx;
            do {
                idx = (int)(rng.nextFloat() * DEATH_MSGS.length);
            } while(idx == lastDeathMsg);

            lastDeathMsg = idx;
            currentDeathMsg = DEATH_MSGS[idx];
        }
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
            case MENU         -> drawMenu(g);
            case LEVEL_SELECT -> drawLevelSelect(g);
            case PLAYING      -> drawGame(g);
            case PAUSED       -> { drawGame(g); drawPauseMenu(g); }
            case DEAD         -> { drawGame(g); drawDeathScreen(g); }
            case WIN_LEVEL    -> { drawGame(g); drawWinLevel(g); }
            case WIN_GAME     -> drawWinGame(g);
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

        for(int i=0;i<MENU_OPTIONS.length;i++) {
            boolean sel = (menuSel==i);
            g.setFont(new Font("Courier New", Font.BOLD, sel?26:22));
            Color col = sel ? new Color(255,200,80) : new Color(160,160,200);
            if(sel){ g.setColor(new Color(255,200,80,40)); g.fillRoundRect(W/2-130,283+i*55-28,260,36,8,8); }
            g.setColor(col);
            g.drawString(MENU_OPTIONS[i], W/2-g.getFontMetrics().stringWidth(MENU_OPTIONS[i])/2, 283+i*55);
        }

        g.setFont(new Font("Courier New", Font.PLAIN, 12));
        g.setColor(new Color(150,150,170));
        g.drawString("A/D: Move   SPACE: Jump   ↑↓: Select   ENTER: Confirm", W/2-230, H-30);

        g.setFont(new Font("Courier New", Font.PLAIN, 12));
        String[] legend = {
            "Red when stepped on = Fake  |  Cyan ↑↑ = Bouncy",
            "Faint shimmer = Invisible  |  Red trails = Chasing spike",
        };
        for(int i=0;i<legend.length;i++){
            g.setColor(new Color(180,150,200));
            g.drawString(legend[i], W/2-g.getFontMetrics().stringWidth(legend[i])/2, 450+i*18);
        }
    }

    // ── LEVEL SELECT SCREEN ───────────────────────────────────
    void drawLevelSelect(Graphics2D g) {
        float t = tick * 0.01f;
        Color c1 = new Color((int)(15+8*Math.sin(t)),(int)(20+10*Math.sin(t+1)),(int)(35+12*Math.sin(t+2)));
        Color c2 = new Color((int)(40+15*Math.sin(t+3)),(int)(30+12*Math.sin(t+4)),(int)(70+18*Math.sin(t+5)));
        g.setPaint(new GradientPaint(0,0,c1,W,H,c2));
        g.fillRect(0,0,W,H);

        // Title
        g.setFont(new Font("Courier New", Font.BOLD, 36));
        String title = "SELECT LEVEL";
        drawShadowText(g, title, W/2-g.getFontMetrics().stringWidth(title)/2, 70,
            new Color(100, 200, 255), new Color(30, 80, 120));

        // Level cards
        int cardW = 140, cardH = 160;
        int spacing = 20;
        int totalW = 5 * cardW + 4 * spacing;
        int startX = W/2 - totalW/2;
        int cardY = 120;

        for(int i = 0; i < 5; i++) {
            int cx = startX + i * (cardW + spacing);
            boolean selected = (levelSelectSel == i);
            boolean unlocked = (i <= highestUnlockedLevel);

            // Card shadow
            g.setColor(new Color(0, 0, 0, 80));
            g.fillRoundRect(cx + 4, cardY + 4, cardW, cardH, 12, 12);

            // Card background
            if(unlocked) {
                Color cardBg = selected ? new Color(40, 60, 100, 240) : new Color(25, 35, 60, 220);
                g.setColor(cardBg);
            } else {
                g.setColor(new Color(30, 30, 40, 200));
            }
            g.fillRoundRect(cx, cardY, cardW, cardH, 12, 12);

            // Border
            if(selected && unlocked) {
                g.setColor(new Color(255, 200, 80, 200));
                g.setStroke(new BasicStroke(3f));
            } else if(unlocked) {
                g.setColor(new Color(100, 140, 200, 150));
                g.setStroke(new BasicStroke(1.5f));
            } else {
                g.setColor(new Color(60, 60, 80, 150));
                g.setStroke(new BasicStroke(1.5f));
            }
            g.drawRoundRect(cx, cardY, cardW, cardH, 12, 12);
            g.setStroke(new BasicStroke(1f));

            // Level number
            g.setFont(new Font("Courier New", Font.BOLD, 48));
            String num = String.valueOf(i + 1);
            int numW = g.getFontMetrics().stringWidth(num);
            if(unlocked) {
                g.setColor(selected ? new Color(255, 220, 100) : new Color(180, 200, 255));
            } else {
                g.setColor(new Color(80, 80, 100));
            }
            g.drawString(num, cx + cardW/2 - numW/2, cardY + 60);

            // Level name
            g.setFont(new Font("Courier New", Font.BOLD, 11));
            String name = LEVEL_NAMES[i];
            int nameW = g.getFontMetrics().stringWidth(name);
            if(unlocked) {
                g.setColor(selected ? new Color(255, 240, 200) : new Color(160, 180, 220));
            } else {
                g.setColor(new Color(70, 70, 90));
            }
            g.drawString(name, cx + cardW/2 - nameW/2, cardY + 85);

            // Lock icon for locked levels
            if(!unlocked) {
                g.setFont(new Font("Courier New", Font.BOLD, 28));
                g.setColor(new Color(100, 80, 120));
                String lock = "🔒";
                int lockW = g.getFontMetrics().stringWidth(lock);
                g.drawString(lock, cx + cardW/2 - lockW/2, cardY + 125);
            } else {
                // Difficulty indicator
                g.setFont(new Font("Courier New", Font.PLAIN, 10));
                g.setColor(new Color(140, 140, 180));
                String diff = getDifficultyLabel(i);
                int diffW = g.getFontMetrics().stringWidth(diff);
                g.drawString(diff, cx + cardW/2 - diffW/2, cardY + 105);

                // Stars or checkmark if completed
                if(i < highestUnlockedLevel || (i == 4 && highestUnlockedLevel >= 4)) {
                    g.setColor(new Color(100, 255, 150));
                    g.setFont(new Font("Courier New", Font.BOLD, 16));
                    String check = "✓";
                    int checkW = g.getFontMetrics().stringWidth(check);
                    g.drawString(check, cx + cardW/2 - checkW/2, cardY + 140);
                }
            }

            // Selection indicator
            if(selected && unlocked) {
                g.setColor(new Color(255, 200, 80, (int)(100 + 50 * Math.sin(tick * 0.1))));
                g.fillRoundRect(cx - 2, cardY - 2, cardW + 4, 4, 2, 2);
            }
        }

        // Instructions
        g.setFont(new Font("Courier New", Font.PLAIN, 13));
        g.setColor(new Color(180, 180, 210));
        String inst = "← →  Select Level   |   ENTER / SPACE  Start   |   ESC  Back";
        g.drawString(inst, W/2 - g.getFontMetrics().stringWidth(inst)/2, cardY + cardH + 50);

        // Hint about locked levels
        if(levelSelectSel > highestUnlockedLevel) {
            g.setFont(new Font("Courier New", Font.ITALIC, 12));
            g.setColor(new Color(255, 150, 100));
            String lockHint = "Complete previous levels to unlock!";
            g.drawString(lockHint, W/2 - g.getFontMetrics().stringWidth(lockHint)/2, cardY + cardH + 75);
        }

        // Progress info
        g.setFont(new Font("Courier New", Font.PLAIN, 11));
        g.setColor(new Color(140, 160, 200));
        String progress = "Progress: " + (highestUnlockedLevel + 1) + "/5 levels unlocked";
        g.drawString(progress, W/2 - g.getFontMetrics().stringWidth(progress)/2, H - 40);
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

    // ── GAME ──────────────────────────────────────────────────
    void drawGame(Graphics2D g) {
        int cx = (int)camX;

        g.setPaint(new GradientPaint(0,0,new Color(15,5,30),0,H,new Color(35,15,60)));
        g.fillRect(0,0,W,H);

        g.setColor(new Color(200,200,220,140));
        for(int i=0;i<80;i++){
            int sx = ((i*97+i*43) % levelW - cx/2 + levelW*2) % W;
            int sy = (i*53+i*11) % (H/2);
            g.fillRect(sx, sy, i%7==0?2:1, i%7==0?2:1);
        }

        if(currentLevel == 0) drawTutorialZoneMarkers(g, cx);

        for(int pIdx=0; pIdx<platforms.size(); pIdx++) {
            Platform p = platforms.get(pIdx);
            int rx = p.x - cx;
            if(rx+p.w < -10 || rx > W+10) continue;

            ArrayList<int[]> holeMasks = new ArrayList<>();
            for(AppearingHole h : holes) {
                if(h.platformIndex == pIdx && h.active)
                    holeMasks.add(new int[]{h.holeX, h.holeW});
                else if(h.platformIndex == pIdx && !h.triggered && h.revealDelay <= 60) {
                    if((blinkTick/5)%2==0) {
                        g.setColor(new Color(255,200,0,100));
                        g.fillRect(rx+h.holeX, p.y, h.holeW, p.h);
                    }
                }
            }

            if(p.invisible) {
                if(p.revealTimer_active) {
                    g.setColor(new Color(180,180,255,160));
                    drawPlatformSegmented(g, rx, p.y, p.w, p.h, holeMasks);
                } else {
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
                // FAKE PLATFORM
                float crumble = Math.min(1f, p.fakeTimer/25f);
                g.setColor(new Color((int)(40+crumble*180),(int)(25*(1-crumble)),10));
                g.fillRect(rx, p.y, p.w, p.h);
                g.setColor(new Color(200,50,0));
                for(int i=0;i<3;i++) 
                    g.drawLine(rx+i*p.w/3,p.y,rx+i*p.w/3+5,p.y+p.h);

            } else if(p.fake) {
                // FAKE PLATFORM APPEARANCE
                Color topCol = new Color(60+currentLevel*8,100-currentLevel*4,60+currentLevel*4);
                g.setColor(new Color(50,35,15));
                drawPlatformSegmented(g, rx, p.y+7, p.w, p.h-7, holeMasks);
                g.setColor(topCol);
                drawPlatformSegmented(g, rx, p.y, p.w, 7, holeMasks);

            } else if(p.bouncy) {
                //BOUNCY PLATFORM APPEARANCE
                float bounce = 0.5f+0.5f*(float)Math.sin(tick*0.2f);
                int b=(int)(50+80*bounce);
                g.setColor(new Color(b,(int)(150+50*bounce),(int)(200+50*bounce)));
                drawPlatformSegmented(g, rx, p.y, p.w, p.h, holeMasks);
                g.setColor(new Color(150,255,255));
                g.fillRect(rx,p.y,p.w,5);
                g.setColor(Color.WHITE);
                g.setFont(new Font("Courier New",Font.BOLD,9));
                if(p.w>40) 
                    g.drawString("↑↑↑",rx+p.w/2-10,p.y-2);

            } else if(p.shiftOnStep) {
                // MOVING PLATFORM
                Color topCol = new Color(60+currentLevel*8,100-currentLevel*4,60+currentLevel*4);
                g.setColor(new Color(50,35,15));
                drawPlatformSegmented(g, rx, p.y+7, p.w, p.h-7, holeMasks);
                g.setColor(topCol);
                drawPlatformSegmented(g, rx, p.y, p.w, 7, holeMasks);
                
                } else {
                // GROUND PLATFORM
                Color topCol = new Color(60+currentLevel*8,100-currentLevel*4,60+currentLevel*4);
                g.setColor(new Color(50,35,15));
                drawPlatformSegmented(g, rx, p.y+7, p.w, p.h-7, holeMasks);
                g.setColor(topCol);
                drawPlatformSegmented(g, rx, p.y, p.w, 7, holeMasks);
            }
        }

        for(Spike s : spikes) {
            int sx = s.x - cx;
            if(sx < -30 || sx > W+30) continue;
            if(s.chasing) {
                g.setColor(new Color(255,50,0,60));
                g.fillOval(sx-8, s.y-8, s.w+16, s.h+16);
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

        for(Cannon c : cannons) {
            int ccx = c.x - cx;
            if(ccx<-60||ccx>W+60) continue;
            drawCannon(g, ccx, c.y, c.facingRight);
        }

        for(Cannonball cb : cannonballs) {
            int bx = (int)(cb.x - cx);
            if(bx<-20||bx>W+20) continue;
            g.setColor(new Color(255,100,0,60)); g.fillOval(bx-10,(int)cb.y-10,20,20);
            g.setColor(new Color(50,50,50));     g.fillOval(bx-6,(int)cb.y-6,12,12);
            g.setColor(new Color(100,100,100));  g.fillOval(bx-4,(int)cb.y-4,8,8);
        }

        drawGoal(g, goalX-cx, goalY);

        for(Particle p : particles) {
            float alpha = (float)p.life/p.maxLife;
            g.setColor(new Color(p.color.getRed(),p.color.getGreen(),p.color.getBlue(),(int)(alpha*255)));
            g.fillOval((int)(p.x-cx)-3,(int)p.y-3,6,6);
        }

        if(state == State.PLAYING || state == State.PAUSED)
            drawPlayerChar(g,(int)(px-cx),(int)py,facingRight,onGround?(tick/6)%4:1);

        drawHUD(g);

        if(currentLevel == 0 && activeTutCard != null) drawTutorialCard(g, activeTutCard);
        if(currentLevel > 0 && hintVisible && currentLevel < LEVEL_HINTS.length)
            drawHint(g, LEVEL_HINTS[currentLevel][hintIndex], hintFadeTimer);
    }

    // ── PAUSE MENU ────────────────────────────────────────────
    void drawPauseMenu(Graphics2D g) {
        g.setColor(new Color(0, 0, 0, 170));
        g.fillRect(0, 0, W, H);

        if(pauseLevelSelect) {
            drawPauseLevelSelect(g);
            return;
        }

        int pw = 420, ph = 340;
        int panX = W/2 - pw/2, panY = H/2 - ph/2;

        g.setColor(new Color(0, 0, 0, 100));
        g.fillRoundRect(panX+6, panY+6, pw, ph, 20, 20);

        g.setPaint(new GradientPaint(panX, panY, new Color(12, 8, 28, 245),
            panX, panY+ph, new Color(22, 12, 45, 245)));
        g.fillRoundRect(panX, panY, pw, ph, 20, 20);

        g.setColor(new Color(120, 80, 200, 200));
        g.setStroke(new BasicStroke(2f));
        g.drawRoundRect(panX, panY, pw, ph, 20, 20);
        g.setStroke(new BasicStroke(1f));

        g.setColor(new Color(150, 80, 255, 200));
        g.fillRoundRect(panX, panY+12, 5, ph-24, 4, 4);

        g.setFont(new Font("Courier New", Font.BOLD, 28));
        String title = "II  PAUSED";
        drawShadowText(g, title, W/2 - g.getFontMetrics().stringWidth(title)/2, panY + 44,
            new Color(180, 130, 255), new Color(60, 20, 100));

        g.setColor(new Color(120, 80, 200, 80));
        g.fillRect(panX + 20, panY + 54, pw - 40, 1);

        int optStartY = panY + 85;
        int optSpacing = 44;
        for(int i = 0; i < PAUSE_OPTIONS.length; i++) {
            boolean sel = (pauseSel == i);
            int oy = optStartY + i * optSpacing;

            if(sel) {
                g.setColor(new Color(120, 80, 200, 50));
                g.fillRoundRect(panX + 14, oy - 22, pw - 28, 34, 8, 8);
                g.setColor(new Color(150, 100, 255, 130));
                g.setStroke(new BasicStroke(1.5f));
                g.drawRoundRect(panX + 14, oy - 22, pw - 28, 34, 8, 8);
                g.setStroke(new BasicStroke(1f));
                g.setColor(new Color(255, 200, 80));
                g.setFont(new Font("Courier New", Font.BOLD, 14));
                g.drawString("›", panX + 22, oy + 2);
            }

            if(i == 3) { // Volume
                g.setFont(new Font("Courier New", Font.BOLD, sel ? 18 : 16));
                g.setColor(sel ? new Color(255, 200, 80) : new Color(160, 140, 200));
                g.drawString(PAUSE_OPTIONS[i], panX + 40, oy + 2);

                int barX = panX + 190, barY = oy - 10, barW = 180, barH = 10;
                g.setColor(new Color(60, 40, 100));
                g.fillRoundRect(barX, barY, barW, barH, 5, 5);
                int fillW = (int)(barW * (volume / 100.0));
                Color barFill = sel ? new Color(200, 140, 255) : new Color(120, 80, 180);
                g.setColor(barFill);
                g.fillRoundRect(barX, barY, fillW, barH, 5, 5);
                g.setColor(sel ? new Color(255, 220, 100) : new Color(200, 170, 255));
                g.fillOval(barX + fillW - 7, barY - 3, 14, 14);
                g.setFont(new Font("Courier New", Font.PLAIN, 11));
                g.setColor(new Color(200, 180, 230));
                g.drawString(volume + "%", barX + barW + 6, barY + 9);

                if(sel) {
                    g.setFont(new Font("Courier New", Font.PLAIN, 10));
                    g.setColor(new Color(180, 160, 220, 180));
                    g.drawString("← / → to adjust", barX, barY + 24);
                }
            } else {
                g.setFont(new Font("Courier New", Font.BOLD, sel ? 18 : 16));
                g.setColor(sel ? new Color(255, 200, 80) : new Color(160, 140, 200));
                g.drawString(PAUSE_OPTIONS[i], panX + 40, oy + 2);
            }
        }

        g.setFont(new Font("Courier New", Font.PLAIN, 11));
        g.setColor(new Color(140, 120, 180, 180));
        String hint = "↑ ↓ : Navigate   ENTER / SPACE : Select   ESC : Resume";
        g.drawString(hint, W/2 - g.getFontMetrics().stringWidth(hint)/2, panY + ph - 12);
    }

    // ── PAUSE LEVEL SELECT SUB-MENU ───────────────────────────
    void drawPauseLevelSelect(Graphics2D g) {
        int pw = 500, ph = 280;
        int panX = W/2 - pw/2, panY = H/2 - ph/2;

        g.setColor(new Color(0, 0, 0, 100));
        g.fillRoundRect(panX+6, panY+6, pw, ph, 20, 20);

        g.setPaint(new GradientPaint(panX, panY, new Color(12, 18, 35, 245),
            panX, panY+ph, new Color(18, 28, 50, 245)));
        g.fillRoundRect(panX, panY, pw, ph, 20, 20);

        g.setColor(new Color(80, 150, 200, 200));
        g.setStroke(new BasicStroke(2f));
        g.drawRoundRect(panX, panY, pw, ph, 20, 20);
        g.setStroke(new BasicStroke(1f));

        g.setFont(new Font("Courier New", Font.BOLD, 22));
        String title = "⊞  SELECT LEVEL";
        drawShadowText(g, title, W/2 - g.getFontMetrics().stringWidth(title)/2, panY + 36,
            new Color(100, 200, 255), new Color(30, 80, 120));

        // Level buttons
        int btnW = 80, btnH = 90;
        int spacing = 12;
        int totalBtnW = 5 * btnW + 4 * spacing;
        int startX = panX + pw/2 - totalBtnW/2;
        int btnY = panY + 60;

        for(int i = 0; i < 5; i++) {
            int bx = startX + i * (btnW + spacing);
            boolean selected = (pauseLevelSel == i);
            boolean unlocked = (i <= highestUnlockedLevel);

            // Button background
            if(unlocked) {
                Color btnBg = selected ? new Color(50, 80, 130, 230) : new Color(30, 50, 80, 200);
                g.setColor(btnBg);
            } else {
                g.setColor(new Color(40, 40, 55, 180));
            }
            g.fillRoundRect(bx, btnY, btnW, btnH, 10, 10);

            // Border
            if(selected && unlocked) {
                g.setColor(new Color(255, 200, 80, 220));
                g.setStroke(new BasicStroke(2.5f));
            } else if(unlocked) {
                g.setColor(new Color(100, 160, 220, 150));
                g.setStroke(new BasicStroke(1.5f));
            } else {
                g.setColor(new Color(70, 70, 90, 150));
                g.setStroke(new BasicStroke(1f));
            }
            g.drawRoundRect(bx, btnY, btnW, btnH, 10, 10);
            g.setStroke(new BasicStroke(1f));

            // Level number
            g.setFont(new Font("Courier New", Font.BOLD, 32));
            String num = String.valueOf(i + 1);
            int numW = g.getFontMetrics().stringWidth(num);
            if(unlocked) {
                g.setColor(selected ? new Color(255, 220, 100) : new Color(180, 210, 255));
            } else {
                g.setColor(new Color(90, 90, 110));
            }
            g.drawString(num, bx + btnW/2 - numW/2, btnY + 40);

            // Level name
            g.setFont(new Font("Courier New", Font.PLAIN, 9));
            String name = LEVEL_NAMES[i];
            int nameW = g.getFontMetrics().stringWidth(name);
            if(unlocked) {
                g.setColor(selected ? new Color(255, 240, 200) : new Color(150, 180, 220));
            } else {
                g.setColor(new Color(80, 80, 100));
            }
            g.drawString(name, bx + btnW/2 - nameW/2, btnY + 58);

            // Lock or checkmark
            if(!unlocked) {
                g.setFont(new Font("Courier New", Font.BOLD, 18));
                g.setColor(new Color(100, 80, 120));
                g.drawString("🔒", bx + btnW/2 - 10, btnY + 80);
            } else if(i < highestUnlockedLevel) {
                g.setFont(new Font("Courier New", Font.BOLD, 14));
                g.setColor(new Color(100, 255, 150));
                g.drawString("✓", bx + btnW/2 - 5, btnY + 78);
            }

            // Current level indicator
            if(i == currentLevel && unlocked) {
                g.setFont(new Font("Courier New", Font.PLAIN, 8));
                g.setColor(new Color(255, 180, 80));
                String curr = "CURRENT";
                int currW = g.getFontMetrics().stringWidth(curr);
                g.drawString(curr, bx + btnW/2 - currW/2, btnY + btnH + 12);
            }
        }

        // Instructions
        g.setFont(new Font("Courier New", Font.PLAIN, 12));
        g.setColor(new Color(160, 180, 220));
        String inst = "← →  Select   |   ENTER  Start   |   ESC  Back";
        g.drawString(inst, W/2 - g.getFontMetrics().stringWidth(inst)/2, panY + ph - 40);

        // Lock hint
        if(pauseLevelSel > highestUnlockedLevel) {
            g.setFont(new Font("Courier New", Font.ITALIC, 11));
            g.setColor(new Color(255, 150, 100));
            String lockHint = "Complete previous levels to unlock!";
            g.drawString(lockHint, W/2 - g.getFontMetrics().stringWidth(lockHint)/2, panY + ph - 18);
        }
    }

    // ── Tutorial zone section labels ─────────────────────────
    void drawTutorialZoneMarkers(Graphics2D g, int cx) {
        String[][] zones = {
            {  "200", "ZONE 1: GAPS" },
            {  "1200", "ZONE 2: SPIKES" },
            {  "1580", "ZONE 3: FAKE" },
            {  "1980", "ZONE 4: BOUNCY" },
            {  "2340", "ZONE 5: MOVING" },
            {  "2800", "ZONE 6: INVISIBLE" },
            {  "2990", "ZONE 7: HOLES" },
            {  "3100", "ZONE 8: CANNONS" },
        };
        g.setFont(new Font("Courier New", Font.BOLD, 10));
        for(String[] z : zones) {
            int wx = Integer.parseInt(z[0]);
            int sx = wx - cx;
            if(sx < -100 || sx > W + 100) continue;
            g.setColor(new Color(255,255,255,22));
            g.drawString(z[1], sx, H - 60);
        }
    }

    // ── Tutorial Card Renderer ─────────────────────────────────
    void drawTutorialCard(Graphics2D g, TutorialCard card) {
        int progress = card.displayTimer;
        int total    = TutorialCard.DISPLAY_TICKS;

        float alpha = 1f;
        if(progress < 20)       alpha = progress / 20f;
        else if(progress > total - 30) alpha = (total - progress) / 30f;
        alpha = Math.max(0, Math.min(1, alpha));

        int cardW = 390, lineH = 22;
        int numLines = card.lines.length;
        int cardH = 28 + 26 + numLines * lineH + 14;
        int cardX = W/2 - cardW/2, cardY = 22;

        g.setColor(new Color(0,0,0,(int)(alpha*120)));
        g.fillRoundRect(cardX+4, cardY+4, cardW, cardH, 14, 14);
        g.setColor(new Color(8, 6, 20, (int)(alpha*230)));
        g.fillRoundRect(cardX, cardY, cardW, cardH, 14, 14);

        Color ac = card.accentColor;
        g.setColor(new Color(ac.getRed(), ac.getGreen(), ac.getBlue(), (int)(alpha*200)));
        g.setStroke(new BasicStroke(2.2f));
        g.drawRoundRect(cardX, cardY, cardW, cardH, 14, 14);
        g.setStroke(new BasicStroke(1f));

        g.setColor(new Color(ac.getRed(), ac.getGreen(), ac.getBlue(), (int)(alpha*180)));
        g.fillRoundRect(cardX, cardY, 6, cardH, 6, 6);
        g.setColor(new Color(ac.getRed()/5, ac.getGreen()/5, ac.getBlue()/5, (int)(alpha*180)));
        g.fillRoundRect(cardX+1, cardY+1, cardW-2, 26, 12, 12);

        g.setFont(new Font("Courier New", Font.BOLD, 14));
        g.setColor(new Color(ac.getRed(), ac.getGreen(), ac.getBlue(), (int)(alpha*255)));
        g.drawString("▸  " + card.title, cardX + 18, cardY + 18);

        int barW = cardW - 20;
        float pct = 1f - (float)progress / total;
        g.setColor(new Color(ac.getRed(), ac.getGreen(), ac.getBlue(), (int)(alpha * 40)));
        g.fillRoundRect(cardX + 10, cardY + 26, barW, 4, 2, 2);
        g.setColor(new Color(ac.getRed(), ac.getGreen(), ac.getBlue(), (int)(alpha * 160)));
        g.fillRoundRect(cardX + 10, cardY + 26, (int)(barW * pct), 4, 2, 2);

        g.setFont(new Font("Courier New", Font.PLAIN, 13));
        for(int i=0; i<card.lines.length; i++) {
            float lineAlpha = alpha * Math.min(1f, (progress - i * 8) / 15f);
            lineAlpha = Math.max(0, lineAlpha);
            g.setColor(new Color(220, 220, 240, (int)(lineAlpha * 255)));
            g.drawString(card.lines[i], cardX + 18, cardY + 50 + i * lineH);
        }
    }

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
        g.setColor(new Color(60,60,100));
        g.fillRect(x+2, y+playerH-8+legSwing/2, 5, 8);
        g.fillRect(x+7, y+playerH-8-legSwing/2, 5, 8);
        g.setColor(new Color(220,220,255));
        g.fillRect(x+4, y+12, 6, playerH-20);
        g.setColor(new Color(160,160,200));
        g.fillRect(x+8, y+12, 2, playerH-20);
        g.setColor(new Color(200,200,240));
        g.fillRect(x+1, y+playerH-12, 12, 3);
        g.fillRect(x+2, y+11, 10, 2);
        int armY = y+18;
        g.setColor(new Color(180,180,230));
        if(right){ g.fillRect(x-3,armY+legSwing/3,5,3); g.fillRect(x+playerW-2,armY-legSwing/3,5,3); }
        else      { g.fillRect(x-3,armY-legSwing/3,5,3); g.fillRect(x+playerW-2,armY+legSwing/3,5,3); }
        float bob = (float)Math.sin(dotBobTick*0.08f)*2.5f;
        int dotX = x+3, dotY = (int)(y-2+bob);
        g.setColor(new Color(200,150,255,80)); g.fillOval(dotX-4,dotY-4,16,16);
        g.setColor(new Color(230,200,255));    g.fillOval(dotX,dotY,8,8);
        g.setColor(Color.WHITE);               g.fillOval(dotX+2,dotY+1,3,3);
        g.setColor(new Color(50,30,80));
        if(right) g.fillOval(dotX+5,dotY+3,2,2);
        else      g.fillOval(dotX+1,dotY+3,2,2);
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
        g.setColor(new Color(255,255,255,70));
        String pauseHint = "ESC: Pause";
        g.drawString(pauseHint, W - g.getFontMetrics().stringWidth(pauseHint) - 10, 20);

        g.setFont(new Font("Courier New",Font.PLAIN,11));
        g.setColor(new Color(255,255,255,90));
        g.drawString("A/D: Move   SPACE: Jump   R: Restart   ESC: Pause",W/2-190,H-10);
    }

    String getLevelName() {
        return switch(currentLevel){
            case 0->"Tutorial"; case 1->"Getting There"; case 2->"Troll Central";
            case 3->"Almost There"; case 4->"The Finale"; default->"???";
        };
    }

    // ── DEATH SCREEN ──────────────────────────────────────────
    void drawDeathScreen(Graphics2D g) {
    g.setColor(new Color(0,0,0,160)); 
    g.fillRect(0,0,W,H);
    int pw=500,ph=220,panX=W/2-pw/2,panY=H/2-ph/2;
    g.setColor(new Color(15,5,25,230)); 
    g.fillRoundRect(panX,panY,pw,ph,18,18);
    g.setColor(new Color(200,30,30)); 
    g.setStroke(new BasicStroke(2));
    g.drawRoundRect(panX,panY,pw,ph,18,18);
    g.setStroke(new BasicStroke(1));
    g.setFont(new Font("Courier New",Font.BOLD,42));
    drawShadowText(
        g,
        currentDeathMsg[2],
        W/2-g.getFontMetrics().stringWidth(currentDeathMsg[2])/2,
        panY+55,
        new Color(255,60,60),
        new Color(100,0,0)
    );
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

        // ── MAIN MENU ────────────────────────────────────────
        if(state == State.MENU){
            if(k==KeyEvent.VK_UP  ||k==KeyEvent.VK_W) menuSel=Math.max(0,menuSel-1);
            if(k==KeyEvent.VK_DOWN||k==KeyEvent.VK_S) menuSel=Math.min(2,menuSel+1);
            if(k==KeyEvent.VK_ENTER||k==KeyEvent.VK_SPACE){
                switch(menuSel) {
                    case 0 -> { currentLevel=0; totalScore=0; deaths=0; startLevel(0); }
                    case 1 -> { levelSelectSel=0; state=State.LEVEL_SELECT; }
                    case 2 -> System.exit(0);
                }
            }
            return;
        }

        // ── LEVEL SELECT ─────────────────────────────────────
        if(state == State.LEVEL_SELECT) {
            if(k==KeyEvent.VK_LEFT ||k==KeyEvent.VK_A) levelSelectSel = Math.max(0, levelSelectSel-1);
            if(k==KeyEvent.VK_RIGHT||k==KeyEvent.VK_D) levelSelectSel = Math.min(4, levelSelectSel+1);
            if(k==KeyEvent.VK_ENTER||k==KeyEvent.VK_SPACE) {
                if(levelSelectSel <= highestUnlockedLevel) {
                    currentLevel = levelSelectSel;
                    totalScore = 0;
                    deaths = 0;
                    startLevel(currentLevel);
                }
            }
            if(k==KeyEvent.VK_ESCAPE) state = State.MENU;
            return;
        }

        // ── WIN GAME ─────────────────────────────────────────
        if(state==State.WIN_GAME){
            if(k==KeyEvent.VK_ESCAPE){ state=State.MENU; particles.clear(); }
            return;
        }

        // ── PAUSE MENU ───────────────────────────────────────
        if(state == State.PAUSED) {
            handlePauseKey(k);
            return;
        }

        // ── PLAYING / DEAD ───────────────────────────────────
        if(k==KeyEvent.VK_A||k==KeyEvent.VK_LEFT)  leftDown=true;
        if(k==KeyEvent.VK_D||k==KeyEvent.VK_RIGHT) rightDown=true;
        if(k==KeyEvent.VK_SPACE||k==KeyEvent.VK_UP||k==KeyEvent.VK_W) jumpDown=true;
        if(k==KeyEvent.VK_R) {
            if(state==State.DEAD||state==State.PLAYING) startLevel(currentLevel);
        }
        if(k==KeyEvent.VK_ESCAPE) {
            if(state==State.PLAYING) {
                leftDown=false; rightDown=false; jumpDown=false;
                pauseSel=0;
                pauseLevelSelect=false;
                state=State.PAUSED;
            } else if(state==State.DEAD) {
                state=State.MENU; particles.clear(); leftDown=rightDown=jumpDown=false;
            }
        }
    }

    void handlePauseKey(int k) {
        // If in level select sub-menu
        if(pauseLevelSelect) {
            if(k==KeyEvent.VK_LEFT ||k==KeyEvent.VK_A) pauseLevelSel = Math.max(0, pauseLevelSel-1);
            if(k==KeyEvent.VK_RIGHT||k==KeyEvent.VK_D) pauseLevelSel = Math.min(4, pauseLevelSel+1);
            if(k==KeyEvent.VK_ENTER||k==KeyEvent.VK_SPACE) {
                if(pauseLevelSel <= highestUnlockedLevel) {
                    currentLevel = pauseLevelSel;
                    pauseLevelSelect = false;
                    startLevel(currentLevel);
                }
            }
            if(k==KeyEvent.VK_ESCAPE) pauseLevelSelect = false;
            return;
        }

        // Volume adjustment mode
        if(pauseSel == 3) {
            if(k==KeyEvent.VK_LEFT) {
                volume = Math.max(0, volume - 5);
                applyVolume();
                return;
            }
            if(k==KeyEvent.VK_RIGHT) {
                volume = Math.min(100, volume + 5);
                applyVolume();
                return;
            }
        }

        // Navigation
        if(k==KeyEvent.VK_UP||k==KeyEvent.VK_W)
            pauseSel = (pauseSel - 1 + PAUSE_OPTIONS.length) % PAUSE_OPTIONS.length;
        if(k==KeyEvent.VK_DOWN||k==KeyEvent.VK_S)
            pauseSel = (pauseSel + 1) % PAUSE_OPTIONS.length;

        // Confirm selection
        if(k==KeyEvent.VK_ENTER||k==KeyEvent.VK_SPACE) {
            switch(pauseSel) {
                case 0 -> { // Resume
                    state = State.PLAYING;
                }
                case 1 -> { // Restart level
                    startLevel(currentLevel);
                }
                case 2 -> { // Level Select
                    pauseLevelSel = currentLevel;
                    pauseLevelSelect = true;
                }
                case 3 -> { // Volume
                    volume = Math.min(100, volume + 10);
                    if(volume > 100) volume = 0;
                    applyVolume();
                }
                case 4 -> { // Quit to menu
                    state = State.MENU;
                    particles.clear();
                    leftDown=false; rightDown=false; jumpDown=false;
                }
            }
        }

        // ESC resumes
        if(k==KeyEvent.VK_ESCAPE) {
            state = State.PLAYING;
        }
    }

    void applyVolume() {
        // Placeholder — wire up to your Clip's FloatControl here.
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
