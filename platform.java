import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.*;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.Random;

public class platform extends JPanel implements ActionListener, KeyListener {

    // ── Window ──────────────────────────────────────────────
    static final int W = 900, H = 550;

    // ── Physics ─────────────────────────────────────────────
    static final float GRAVITY  = 0.9f;
    static final float JUMP_VEL = -13f;
    static final float MOVE_SPD = 6f;
    static final float MAX_FALL = 16f;

    // ── Game States ─────────────────────────────────────────
    enum State { MENU, PLAYING, DEAD, WIN_LEVEL, WIN_GAME }
    State state = State.MENU;

    int currentLevel = 0;
    int totalScore   = 0;
    int deaths       = 0;

    // ── Player (lowercase i) ─────────────────────────────────
    float px = 80, py = 300, pvx = 0, pvy = 0;
    boolean onGround    = false;
    boolean facingRight = true;
    int playerW = 14, playerH = 36;
    int dotBobTick = 0;

    // ── Input ────────────────────────────────────────────────
    boolean leftDown, rightDown, jumpDown, jumpConsumed;

    // ── Camera ───────────────────────────────────────────────
    float camX = 0;

    // ── Platform ─────────────────────────────────────────────
    static class Platform {
        int x, y, w, h;
        boolean fake;      // falls after stepping on
        boolean bouncy;    // launches player up
        boolean moving;    // moves left/right
        float mx, my, mrange, mspeed, mdir = 1;
        int   fakeTimer = 0;
        boolean fakeTriggered = false;

        Platform(int x, int y, int w, int h) {
            this.x = x; this.y = y; this.w = w; this.h = h;
        }
        Rectangle rect() { return new Rectangle(x, y, w, h); }
    }
    ArrayList<Platform> platforms = new ArrayList<>();

    // ── Hazards ──────────────────────────────────────────────
    static class Spike {
        int x, y, w, h;
        Spike(int x, int y, int w, int h){ this.x=x;this.y=y;this.w=w;this.h=h; }
        Rectangle rect(){ return new Rectangle(x,y,w,h); }
    }
    ArrayList<Spike> spikes = new ArrayList<>();

    // ── Cannons ──────────────────────────────────────────────
    static class Cannon {
        int x, y;
        boolean facingRight;
        int fireTimer, fireRate;
        Cannon(int x, int y, boolean fr, int rate){
            this.x=x;this.y=y;this.facingRight=fr;this.fireRate=rate;this.fireTimer=rate/2;
        }
    }
    static class Cannonball {
        float x, y, vx;
        boolean alive = true;
        Cannonball(float x, float y, float vx){ this.x=x;this.y=y;this.vx=vx; }
        Rectangle rect(){ return new Rectangle((int)x-6,(int)y-6,12,12); }
    }
    ArrayList<Cannon> cannons = new ArrayList<>();
    ArrayList<Cannonball> cannonballs = new ArrayList<>();

    // ── Fake floor (invisible pit) ────────────────────────────
    // encoded as invisible regions that look like floor but have a gap
    static class FakeFloor {
        int x, y, w;
        FakeFloor(int x, int y, int w){ this.x=x;this.y=y;this.w=w; }
    }
    ArrayList<FakeFloor> fakeFloors = new ArrayList<>();

    // ── Goal / Checkpoint ────────────────────────────────────
    int goalX, goalY;
    static final int GOAL_W = 40, GOAL_H = 60;
    int levelW;

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

    // ── Death messages (Henry Stickmin style) ─────────────────
    static final String[][] DEATH_MSGS = {
        { "Walked into a spike.", "Bro really tried.", "FAILURE" },
        { "Got yeeted by a cannonball.", "Skill issue, honestly.", "KABOOM" },
        { "Fell into a fake floor.", "The floor was NOT there.", "TROLLED" },
        { "Stepped on a bouncy pad.", "Went straight to heaven.", "YEET" },
        { "Fell into a pit.", "Didn't jump. Incredible.", "GRAVITY" },
        { "Got hit mid-air.", "Physics said no.", "NOPE" },
        { "Died to the easiest part.", "Really? THAT killed you?", "YIKES" },
        { "The moving platform moved.", "Revolutionary discovery.", "WOW" },
        { "Touched a spike sideways.", "Hitbox crime detected.", "UNFAIR" },
        { "Stood still too long.", "A cannonball found you.", "AFK" },
        { "Almost made it.", "Key word: almost.", "SO CLOSE" },
        { "Jumped too early.", "Patience was not found.", "EAGER" },
        { "Jumped too late.", "Reaction time: 0.", "SLOW" },
        { "Didn't make the gap.", "Gap was too gappy.", "GAPPED" },
        { "Lost to a fake block.", "It literally blinked.", "TRICKED" },
    };
    String[] currentDeathMsg = DEATH_MSGS[0];

    // ── Misc ──────────────────────────────────────────────────
    Timer timer;
    Random rng = new Random();
    int tick = 0;
    int menuSel = 0; // 0=Start, 1=Quit
    int winTimer = 0;

    // Level-select blink
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
        cannonballs.clear(); fakeFloors.clear(); particles.clear();
        px=60; py=400; pvx=0; pvy=0; camX=0;
        onGround=false; jumpConsumed=false;
        winTimer=0;

        switch(lvl) {
            case 0 -> buildLevel1();
            case 1 -> buildLevel2();
            case 2 -> buildLevel3();
            case 3 -> buildLevel4();
            case 4 -> buildLevel5();
        }
        state = State.PLAYING;
    }

    // ── LEVEL 1: "Tutorial" (lol not really) ─────────────────
    void buildLevel1() {
        levelW = 3000;
        // Ground
        addPlatform(0,   H-50, 300, 50);
        // Gap then fake floor painted like solid (just no platform, empty)
        addPlatform(350, H-50, 200, 50);
        addPlatform(650, H-50, 80,  50);  // tiny
        addPlatform(800, H-50, 200, 50);
        addPlatform(1100,H-50, 150, 50);
        addPlatform(1350,H-50, 300, 50);
        addPlatform(1750,H-50, 100, 50);
        addPlatform(1950,H-50, 250, 50);
        addPlatform(2300,H-50, 200, 50);
        addPlatform(2600,H-50, 400, 50);

        // Floaters
        addPlatform(400, 380, 120, 15);
        Platform p = addPlatform(700, 320, 100, 15);
        p.fake = true;  // <- sneaky fake
        addPlatform(900, 360, 80,  15);
        addPlatform(1150,300, 110, 15);

        // Spikes
        addSpikes(355, H-65, 5, 15); // right after gap, cruel
        addSpikes(800, H-65, 4, 15);
        addSpikes(1400,H-65, 3, 15);
        addSpikes(1750,H-65, 2, 15); // tiny platform has spike at edge

        // Cannons
        cannons.add(new Cannon(1100, H-90, false, 120));
        cannons.add(new Cannon(1950, H-90, false, 90));

        goalX = 2750; goalY = H-110;
        addPlatform(goalX-10, H-50, 120, 50);
    }

    // ── LEVEL 2: "Getting Harder" ──────────────────────────────
    void buildLevel2() {
        levelW = 3500;
        addPlatform(0,   H-50, 200, 50);
        addPlatform(250, H-50, 150, 50);
        addPlatform(500, H-50, 60,  50); // tiny
        addPlatform(650, H-50, 200, 50);
        addPlatform(950, H-50, 100, 50);
        addPlatform(1150,H-50, 180, 50);
        addPlatform(1450,H-50, 80,  50);
        addPlatform(1650,H-50, 250, 50);
        addPlatform(1980,H-50, 120, 50);
        addPlatform(2200,H-50, 200, 50);
        addPlatform(2500,H-50, 80,  50);
        addPlatform(2700,H-50, 300, 50);
        addPlatform(3100,H-50, 400, 50);

        // Floaters
        Platform m1 = addPlatform(300, 350, 100, 15);
        m1.moving=true; m1.mx=300; m1.mrange=100; m1.mspeed=1.5f;

        addPlatform(600, 280, 90, 15);
        Platform f1 = addPlatform(750, 310, 90, 15);
        f1.fake=true;
        addPlatform(900, 270, 80, 15);

        Platform m2 = addPlatform(1200, 300, 80, 15);
        m2.moving=true; m2.mx=1200; m2.mrange=120; m2.mspeed=2f;

        addPlatform(1500, 260, 120, 15);
        Platform b1 = addPlatform(1700, 320, 80, 15);
        b1.bouncy=true;

        addPlatform(1900, 200, 100, 15);
        addPlatform(2100, 250, 90, 15);

        // Spikes everywhere
        addSpikes(255, H-65, 3, 15);
        addSpikes(500, H-65, 1, 15);
        addSpikes(1150,H-65, 4, 15);
        addSpikes(1660,H-65, 2, 15);
        addSpikes(2210,H-65, 3, 15);
        addSpikes(2510,H-65, 1, 15);
        addSpikes(2710,H-65, 5, 15);

        cannons.add(new Cannon(650, H-90, false, 90));
        cannons.add(new Cannon(1450,H-90, false, 80));
        cannons.add(new Cannon(2200,H-90, false, 70));
        cannons.add(new Cannon(3100,H-90, true,  100));

        goalX = 3200; goalY = H-110;
    }

    // ── LEVEL 3: "You Thought" ─────────────────────────────────
    void buildLevel3() {
        levelW = 4000;
        // Broken ground
        addPlatform(0,   H-50, 150, 50);
        addPlatform(200, H-50, 80,  50);
        addPlatform(380, H-50, 60,  50);
        addPlatform(550, H-50, 100, 50);
        addPlatform(760, H-50, 60,  50);
        addPlatform(930, H-50, 120, 50);
        addPlatform(1160,H-50, 80,  50);
        addPlatform(1350,H-50, 100, 50);
        addPlatform(1550,H-50, 60,  50);
        addPlatform(1720,H-50, 80,  50);
        addPlatform(1900,H-50, 120, 50);
        addPlatform(2150,H-50, 60,  50);
        addPlatform(2350,H-50, 100, 50);
        addPlatform(2600,H-50, 60,  50);
        addPlatform(2800,H-50, 150, 50);
        addPlatform(3100,H-50, 80,  50);
        addPlatform(3300,H-50, 80,  50);
        addPlatform(3500,H-50, 100, 50);
        addPlatform(3700,H-50, 300, 50);

        // Moving platforms (required path)
        Platform m1 = addPlatform(230, 380, 90, 15);
        m1.moving=true; m1.mx=230; m1.mrange=130; m1.mspeed=1.8f;
        Platform m2 = addPlatform(550, 310, 80, 15);
        m2.moving=true; m2.mx=550; m2.mrange=100; m2.mspeed=2.2f;

        // Fake platforms in a row (look like stepping stones)
        Platform f1 = addPlatform(800, 350, 80, 15); f1.fake=true;
        Platform f2 = addPlatform(950, 320, 80, 15); f2.fake=true;
        addPlatform(1100, 290, 90, 15);

        addPlatform(1300, 260, 80, 15);
        Platform b1 = addPlatform(1450, 300, 80, 15); b1.bouncy=true;
        addPlatform(1600, 180, 100, 15);

        Platform m3 = addPlatform(1800, 260, 80, 15);
        m3.moving=true; m3.mx=1800; m3.mrange=150; m3.mspeed=2.5f;

        addPlatform(2100, 240, 90, 15);
        Platform f3 = addPlatform(2250, 270, 80, 15); f3.fake=true;
        addPlatform(2400, 250, 80, 15);

        Platform b2 = addPlatform(2700, 300, 80, 15); b2.bouncy=true;
        addPlatform(2900, 150, 90, 15);

        Platform m4 = addPlatform(3150, 200, 80, 15);
        m4.moving=true; m4.mx=3150; m4.mrange=120; m4.mspeed=3f;

        // Spikes
        addSpikes(205, H-65, 2, 15);
        addSpikes(390, H-65, 1, 15);
        addSpikes(560, H-65, 2, 15);
        addSpikes(770, H-65, 1, 15);
        addSpikes(1365,H-65, 2, 15);
        addSpikes(1560,H-65, 1, 15);
        addSpikes(1730,H-65, 2, 15);
        addSpikes(2160,H-65, 1, 15);
        addSpikes(2360,H-65, 2, 15);
        addSpikes(2610,H-65, 1, 15);
        addSpikes(3110,H-65, 2, 15);
        addSpikes(3510,H-65, 3, 15);
        addSpikes(3710,H-65, 4, 15);

        cannons.add(new Cannon(380,  H-90, false, 85));
        cannons.add(new Cannon(930,  H-90, false, 75));
        cannons.add(new Cannon(1550, H-90, false, 70));
        cannons.add(new Cannon(2150, H-90, false, 65));
        cannons.add(new Cannon(2800, H-90, true,  80));
        cannons.add(new Cannon(3500, H-90, false, 60));

        goalX = 3800; goalY = H-110;
    }

    // ── LEVEL 4: "Pure Suffering" ──────────────────────────────
    void buildLevel4() {
        levelW = 4500;
        addPlatform(0,   H-50, 100, 50);
        addPlatform(160, H-50, 60,  50);
        addPlatform(300, H-50, 50,  50);
        addPlatform(440, H-50, 60,  50);
        addPlatform(590, H-50, 50,  50);
        addPlatform(750, H-50, 60,  50);
        addPlatform(900, H-50, 50,  50);
        addPlatform(1050,H-50, 80,  50);
        addPlatform(1230,H-50, 50,  50);
        addPlatform(1400,H-50, 80,  50);
        addPlatform(1580,H-50, 60,  50);
        addPlatform(1750,H-50, 80,  50);
        addPlatform(1950,H-50, 60,  50);
        addPlatform(2130,H-50, 80,  50);
        addPlatform(2400,H-50, 60,  50);
        addPlatform(2600,H-50, 80,  50);
        addPlatform(2850,H-50, 60,  50);
        addPlatform(3100,H-50, 80,  50);
        addPlatform(3350,H-50, 60,  50);
        addPlatform(3600,H-50, 100, 50);
        addPlatform(3850,H-50, 60,  50);
        addPlatform(4000,H-50, 500, 50);

        // Floating path
        for(int i=0;i<8;i++){
            Platform pm = addPlatform(200+i*300, 300-i*20, 70, 15);
            if(i%3==2) pm.fake=true;
            if(i%3==1) { pm.moving=true; pm.mx=200+i*300; pm.mrange=80+i*10; pm.mspeed=1.5f+i*0.3f; }
        }
        Platform b = addPlatform(1800, 260, 70, 15); b.bouncy=true;
        addPlatform(1950, 120, 80, 15);

        Platform m = addPlatform(2300, 200, 70, 15);
        m.moving=true; m.mx=2300; m.mrange=150; m.mspeed=3f;
        Platform f = addPlatform(2600, 230, 70, 15); f.fake=true;
        addPlatform(2800, 200, 70, 15);

        Platform m2 = addPlatform(3200, 180, 70, 15);
        m2.moving=true; m2.mx=3200; m2.mrange=180; m2.mspeed=3.5f;

        // Spikes everywhere on ground platforms
        addSpikes(165, H-65, 1,15); addSpikes(305, H-65,1,15);
        addSpikes(445, H-65,1,15);  addSpikes(595, H-65,1,15);
        addSpikes(755, H-65,1,15);  addSpikes(905, H-65,1,15);
        addSpikes(1235,H-65,1,15);  addSpikes(1405,H-65,1,15);
        addSpikes(1585,H-65,1,15);  addSpikes(1755,H-65,2,15);
        addSpikes(1955,H-65,1,15);  addSpikes(2135,H-65,2,15);
        addSpikes(2405,H-65,1,15);  addSpikes(2605,H-65,1,15);
        addSpikes(2855,H-65,1,15);  addSpikes(3105,H-65,2,15);
        addSpikes(3355,H-65,1,15);  addSpikes(3605,H-65,2,15);
        addSpikes(4005,H-65,5,15);

        cannons.add(new Cannon(160, H-90, false, 70));
        cannons.add(new Cannon(600, H-90, false, 60));
        cannons.add(new Cannon(1050,H-90, false, 55));
        cannons.add(new Cannon(1580,H-90, true,  65));
        cannons.add(new Cannon(2130,H-90, false, 50));
        cannons.add(new Cannon(2600,H-90, false, 55));
        cannons.add(new Cannon(3100,H-90, true,  50));
        cannons.add(new Cannon(3600,H-90, false, 45));

        goalX = 4100; goalY = H-110;
    }

    // ── LEVEL 5: "THE END??? (not really)" ────────────────────
    void buildLevel5() {
        levelW = 5000;
        addPlatform(0, H-50, 80, 50);
        // Giant pit — must use floating platforms
        for(int i=0;i<15;i++){
            int bx = 100 + i*300;
            Platform fp = addPlatform(bx, H-60-rng.nextInt(80), 60+rng.nextInt(40), 15);
            if(i%4==3) fp.fake=true;
            if(i%4==1){ fp.moving=true; fp.mx=bx; fp.mrange=80+rng.nextInt(60); fp.mspeed=1.5f+i*0.15f; }
            if(i%5==4) fp.bouncy=true;
        }

        // Upper path (secret? nope, also dangerous)
        for(int i=0;i<10;i++){
            int bx = 200 + i*400;
            Platform up = addPlatform(bx, 200-i*5, 70, 15);
            if(i%3==1) up.fake=true;
            if(i%3==2){ up.moving=true; up.mx=bx; up.mrange=100+i*10; up.mspeed=2+i*0.2f; }
        }

        // Ground islands
        addPlatform(500, H-50,80,50);
        addPlatform(1000,H-50,60,50);
        addPlatform(1600,H-50,80,50);
        addPlatform(2200,H-50,60,50);
        addPlatform(2900,H-50,80,50);
        addPlatform(3600,H-50,60,50);
        addPlatform(4200,H-50,80,50);
        addPlatform(4600,H-50,400,50);

        // Bouncy trap at end
        Platform bt = addPlatform(4700,H-65,60,15); bt.bouncy=true;
        addPlatform(4800,H-50,200,50);

        // Spikes
        for(int i=0;i<30;i++) addSpikes(150+i*150, H-65, 1, 15);

        // Cannons — lots
        for(int i=0;i<10;i++)
            cannons.add(new Cannon(300+i*400, H-90, i%2==0, 40+i*3));

        goalX = 4850; goalY = H-110;
    }

    // ── Helpers ───────────────────────────────────────────────
    Platform addPlatform(int x, int y, int w, int h) {
        Platform p = new Platform(x, y, w, h);
        platforms.add(p); return p;
    }
    void addSpikes(int x, int y, int count, int size) {
        for(int i=0;i<count;i++) spikes.add(new Spike(x+i*size, y, size, size));
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
            if(winTimer > 120) {
                currentLevel++;
                if(currentLevel >= 5) state = State.WIN_GAME;
                else startLevel(currentLevel);
            }
        }
        repaint();
    }

    void update() {
        // Player movement
        if(leftDown)       { pvx = -MOVE_SPD; facingRight=false; }
        else if(rightDown) { pvx =  MOVE_SPD; facingRight=true; }
        else pvx = 0;

        if(jumpDown && !jumpConsumed && onGround) {
            pvy = JUMP_VEL;
            jumpConsumed = true;
            spawnDust();
        }
        if(!jumpDown) jumpConsumed = false;

        pvy = Math.min(pvy + GRAVITY, MAX_FALL);
        px += pvx;
        py += pvy;

        // Bounds
        if(px < 0) px = 0;
        if(px > levelW - playerW) px = levelW - playerW;

        // Update moving platforms
        for(Platform p : platforms) {
            if(p.moving) {
                p.x += p.mspeed * p.mdir;
                if(p.x > p.mx + p.mrange) p.mdir = -1;
                if(p.x < p.mx)            p.mdir =  1;
            }
            if(p.fake && p.fakeTriggered) {
                p.fakeTimer++;
                if(p.fakeTimer > 30) { p.y += 4; } // fall away
            }
        }

        // Platform collision
        onGround = false;
        Rectangle pr = new Rectangle((int)px,(int)py,playerW,playerH);
        for(Platform p : platforms) {
            if(p.fake && p.fakeTimer > 60) continue; // fully fallen
            Rectangle pl = p.rect();
            if(pr.intersects(pl)) {
                if(pvy >= 0 && py + playerH - pvy <= pl.y + 6) {
                    py = pl.y - playerH;
                    if(p.bouncy) {
                        pvy = JUMP_VEL * 1.4f;
                        spawnBounceParticles((int)px, (int)py);
                    } else {
                        pvy = 0;
                        onGround = true;
                        if(p.fake && !p.fakeTriggered) {
                            p.fakeTriggered = true; // start crumbling
                        }
                    }
                } else if(pvy < 0 && py - pvy >= pl.y + pl.height - 4) {
                    py = pl.y + pl.height; pvy = 1;
                } else if(pvx > 0) { px = pl.x - playerW; }
                  else if(pvx < 0) { px = pl.x + pl.width; }
            }
        }

        // Fall death
        if(py > H + 80) { killPlayer("Fell into a pit.", "Didn't jump. Incredible.", "GRAVITY"); return; }

        // Spike collision
        pr = new Rectangle((int)px+2,(int)py,playerW-4,playerH);
        for(Spike s : spikes) {
            if(pr.intersects(s.rect())) {
                killPlayer("Walked into a spike.", "Bro really tried.", "FAILURE"); return;
            }
        }

        // Cannon update
        for(Cannon c : cannons) {
            c.fireTimer++;
            if(c.fireTimer >= c.fireRate) {
                c.fireTimer = 0;
                float vel = c.facingRight ? 5f : -5f;
                cannonballs.add(new Cannonball(c.x + (c.facingRight?30:-10), c.y+10, vel));
            }
        }
        Iterator<Cannonball> cit = cannonballs.iterator();
        while(cit.hasNext()) {
            Cannonball cb = cit.next();
            cb.x += cb.vx;
            if(cb.x < -50 || cb.x > levelW+50) { cit.remove(); continue; }
            if(pr.intersects(cb.rect())) {
                killPlayer("Got yeeted by a cannonball.", "Skill issue, honestly.", "KABOOM"); return;
            }
        }

        // Camera
        float targetCam = px - W/2f + playerW/2f;
        camX += (targetCam - camX) * 0.12f;
        camX = Math.max(0, Math.min(camX, levelW - W));

        // Goal
        Rectangle goalRect = new Rectangle(goalX, goalY, GOAL_W, GOAL_H);
        if(new Rectangle((int)px,(int)py,playerW,playerH).intersects(goalRect)) {
            totalScore += (5 - currentLevel) * 500 + 1000;
            spawnWinBurst();
            state = State.WIN_LEVEL;
        }

        // Particles
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
        // Pick a message
        int idx = rng.nextInt(DEATH_MSGS.length);
        // Sometimes use the contextual one
        if(rng.nextFloat() < 0.5f) {
            currentDeathMsg = new String[]{l1, l2, tag};
        } else {
            currentDeathMsg = DEATH_MSGS[idx];
        }
        spawnDeathBurst();
        state = State.DEAD;
    }

    void spawnDeathBurst() {
        for(int i=0;i<30;i++){
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
                30+rng.nextInt(20), new Color(
                    rng.nextInt(255), rng.nextInt(255), rng.nextInt(100))));
        }
    }
    void spawnDust() {
        for(int i=0;i<5;i++)
            particles.add(new Particle(px+playerW/2f, py+playerH,
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
            case MENU     -> drawMenu(g);
            case PLAYING  -> drawGame(g);
            case DEAD     -> { drawGame(g); drawDeathScreen(g); }
            case WIN_LEVEL -> { drawGame(g); drawWinLevel(g); }
            case WIN_GAME  -> drawWinGame(g);
        }
    }

    // ── MENU ──────────────────────────────────────────────────
    void drawMenu(Graphics2D g) {
        // Animated gradient bg
        float t = tick * 0.01f;
        Color c1 = new Color(
            (int)(20+10*Math.sin(t)), (int)(5+5*Math.sin(t+1)), (int)(40+10*Math.sin(t+2)));
        Color c2 = new Color(
            (int)(50+20*Math.sin(t+3)), (int)(10+10*Math.sin(t+4)), (int)(80+20*Math.sin(t+5)));
        g.setPaint(new GradientPaint(0,0,c1,W,H,c2));
        g.fillRect(0,0,W,H);

        // Title
        g.setFont(new Font("Courier New", Font.BOLD, 54));
        String title = "i   LOST";
        drawShadowText(g, title, W/2 - g.getFontMetrics().stringWidth(title)/2, 130,
            new Color(255,80,80), new Color(120,0,0));

        g.setFont(new Font("Courier New", Font.PLAIN, 16));
        String sub = "A troll platformer. You will suffer. This is intentional.";
        g.setColor(new Color(200,200,220));
        g.drawString(sub, W/2 - g.getFontMetrics().stringWidth(sub)/2, 175);

        // Draw the little i character as mascot
        drawPlayerChar(g, W/2-7, 210, true, (tick/8)%4);

        // Menu options
        String[] opts = {"▶  START GAME", "✕  QUIT"};
        for(int i=0;i<opts.length;i++) {
            boolean sel = (menuSel == i);
            g.setFont(new Font("Courier New", Font.BOLD, sel ? 26 : 22));
            Color col = sel ? new Color(255,200,80) : new Color(160,160,200);
            if(sel) {
                g.setColor(new Color(255,200,80,40));
                g.fillRoundRect(W/2-120, 310+i*60-28, 240, 36, 8, 8);
            }
            g.setColor(col);
            g.drawString(opts[i], W/2 - g.getFontMetrics().stringWidth(opts[i])/2, 310+i*60);
        }

        // Controls
        g.setFont(new Font("Courier New", Font.PLAIN, 13));
        g.setColor(new Color(150,150,170));
        g.drawString("A/D or ←/→ to move   SPACE to jump   ↑/↓ to select   ENTER to confirm", W/2-270, H-30);

        // Levels preview
        g.setFont(new Font("Courier New", Font.PLAIN, 12));
        g.setColor(new Color(180,120,120));
        String[] lvlNames = {"1: Tutorial (lol)", "2: Getting Harder", "3: You Thought", "4: Pure Suffering", "5: The Finale"};
        for(int i=0;i<5;i++) {
            g.setColor(new Color(200-i*20, 120-i*10, 80+i*10));
            g.drawString(lvlNames[i], 30, 310+i*22);
        }
    }

    // ── GAME ──────────────────────────────────────────────────
    void drawGame(Graphics2D g) {
        int cx = (int)camX;

        // Sky bg
        g.setPaint(new GradientPaint(0,0,new Color(15,5,30),0,H,new Color(35,15,60)));
        g.fillRect(0,0,W,H);

        // Stars (parallax)
        g.setColor(new Color(200,200,220,150));
        for(int i=0;i<80;i++){
            int sx = ((i*97+i*43) % levelW - cx/2 + levelW*2) % W;
            int sy = (i*53+i*11) % (H/2);
            g.fillRect(sx, sy, i%7==0?2:1, i%7==0?2:1);
        }

        // Platforms
        for(Platform p : platforms) {
            int rx = p.x - cx;
            if(rx+p.w < -10 || rx > W+10) continue;

            if(p.fake && p.fakeTriggered) {
                // Crumbling: flash red then fall
                float crumble = Math.min(1f, p.fakeTimer / 30f);
                int r = (int)(40 + crumble*180), gr = (int)(25*(1-crumble));
                g.setColor(new Color(r, gr, 10));
                g.fillRect(rx, p.y, p.w, p.h);
                // Cracks
                g.setColor(new Color(200,50,0));
                for(int i=0;i<3;i++) g.drawLine(rx+i*p.w/3, p.y, rx+i*p.w/3+5, p.y+p.h);
            } else if(p.fake) {
                // Looks normal but blinks slightly to be "fair"
                boolean blink = (blinkTick/25)%2==0;
                g.setColor(blink ? new Color(80,50,120) : new Color(70,40,110));
                g.fillRect(rx, p.y, p.w, p.h);
                g.setColor(new Color(120,80,180));
                g.fillRect(rx, p.y, p.w, 6);
                g.setColor(new Color(90,60,150));
                g.drawRect(rx, p.y, p.w, p.h);
            } else if(p.bouncy) {
                float bounce = 0.5f + 0.5f*(float)Math.sin(tick*0.2f);
                int b = (int)(50+80*bounce);
                g.setColor(new Color(b, (int)(150+50*bounce), (int)(200+50*bounce)));
                g.fillRect(rx, p.y, p.w, p.h);
                g.setColor(new Color(150,255,255));
                g.fillRect(rx, p.y, p.w, 5);
                // Springs indicator
                g.setColor(Color.WHITE);
                g.setFont(new Font("Courier New", Font.BOLD, 9));
                if(p.w > 40) g.drawString("↑↑↑", rx+p.w/2-10, p.y-2);
            } else {
                // Normal platform
                Color topCol = new Color(60+currentLevel*10, 100-currentLevel*5, 60+currentLevel*5);
                Color dirtCol = new Color(50, 35, 15);
                g.setColor(dirtCol);
                g.fillRect(rx, p.y+7, p.w, p.h-7);
                g.setColor(topCol);
                g.fillRect(rx, p.y, p.w, 7);
                g.setColor(new Color(30,60,30));
                g.drawLine(rx, p.y, rx+p.w, p.y);
            }
        }

        // Spikes
        for(Spike s : spikes) {
            int sx = s.x - cx;
            if(sx < -30 || sx > W+30) continue;
            g.setColor(new Color(200,200,220));
            int[] xp = {sx, sx+s.w/2, sx+s.w};
            int[] yp = {s.y+s.h, s.y, s.y+s.h};
            g.fillPolygon(xp, yp, 3);
            g.setColor(new Color(150,180,220));
            g.drawPolygon(xp, yp, 3);
        }

        // Cannons
        for(Cannon c : cannons) {
            int ccx = c.x - cx;
            if(ccx < -60 || ccx > W+60) continue;
            drawCannon(g, ccx, c.y, c.facingRight);
        }

        // Cannonballs
        for(Cannonball cb : cannonballs) {
            int bx = (int)(cb.x - cx);
            if(bx < -20 || bx > W+20) continue;
            // Glow
            g.setColor(new Color(255,100,0,60));
            g.fillOval(bx-10,(int)cb.y-10,20,20);
            g.setColor(new Color(50,50,50));
            g.fillOval(bx-6,(int)cb.y-6,12,12);
            g.setColor(new Color(100,100,100));
            g.fillOval(bx-4,(int)cb.y-4,8,8);
        }

        // Goal
        drawGoal(g, goalX - cx, goalY);

        // Particles
        for(Particle p : particles) {
            float alpha = (float)p.life / p.maxLife;
            g.setColor(new Color(p.color.getRed(), p.color.getGreen(), p.color.getBlue(),
                (int)(alpha*255)));
            g.fillOval((int)(p.x-cx)-3,(int)p.y-3,6,6);
        }

        // Player
        if(state != State.DEAD) {
            drawPlayerChar(g, (int)(px-cx), (int)py, facingRight, onGround?(tick/6)%4:1);
        }

        // HUD
        drawHUD(g);
    }

    void drawPlayerChar(Graphics2D g, int x, int y, boolean right, int frame) {
        // lowercase "i" character:
        // dot on top, thin body

        // Walking/idle animation
        int legSwing = (frame%2==0) ? 3 : -3;

        // Feet (tiny)
        g.setColor(new Color(60,60,100));
        g.fillRect(x+2, y+playerH-8+legSwing/2, 5, 8);
        g.fillRect(x+7, y+playerH-8-legSwing/2, 5, 8);

        // Body (thin rectangle — the stem of i)
        g.setColor(new Color(220,220,255));
        g.fillRect(x+4, y+12, 6, playerH-20);
        // Shadow side
        g.setColor(new Color(160,160,200));
        g.fillRect(x+8, y+12, 2, playerH-20);

        // Serifs (little feet on the stem base)
        g.setColor(new Color(200,200,240));
        g.fillRect(x+1, y+playerH-12, 12, 3);
        // Serif top of stem
        g.fillRect(x+2, y+11, 10, 2);

        // Arms (tiny stubs)
        int armY = y + 18;
        g.setColor(new Color(180,180,230));
        if(right){
            g.fillRect(x-3, armY+legSwing/3, 5, 3);
            g.fillRect(x+playerW-2, armY-legSwing/3, 5, 3);
        } else {
            g.fillRect(x-3, armY-legSwing/3, 5, 3);
            g.fillRect(x+playerW-2, armY+legSwing/3, 5, 3);
        }

        // DOT on top (bobbing)
        float bob = (float)Math.sin(dotBobTick * 0.08f) * 2.5f;
        int dotX = x + 3;
        int dotY = (int)(y - 2 + bob);

        // Dot glow
        g.setColor(new Color(200,150,255,80));
        g.fillOval(dotX-4, dotY-4, 16, 16);
        // Dot body
        g.setColor(new Color(230,200,255));
        g.fillOval(dotX, dotY, 8, 8);
        // Dot shine
        g.setColor(Color.WHITE);
        g.fillOval(dotX+2, dotY+1, 3, 3);

        // Face (tiny, on dot)
        g.setColor(new Color(50,30,80));
        if(right){
            g.fillOval(dotX+5, dotY+3, 2, 2); // eye
        } else {
            g.fillOval(dotX+1, dotY+3, 2, 2);
        }

        // Expression changes when moving fast
        if(Math.abs(pvx) > 2 || Math.abs(pvy) > 5) {
            // scared face
            g.setColor(new Color(50,30,80));
            g.drawArc(dotX+1, dotY+3, 6, 4, 0, 180);
        }
    }

    void drawCannon(Graphics2D g, int x, int y, boolean right) {
        // Base
        g.setColor(new Color(60,40,20));
        g.fillRect(x-5, y+20, 40, 20);
        // Wheels
        g.setColor(new Color(40,25,10));
        g.fillOval(x-2, y+28, 16, 16); g.fillOval(x+18, y+28, 16, 16);
        g.setColor(new Color(80,50,20));
        g.fillOval(x+2, y+32, 8, 8); g.fillOval(x+22, y+32, 8, 8);
        // Barrel
        g.setColor(new Color(70,70,80));
        int bx = right ? x+20 : x-10;
        g.fillRoundRect(bx, y+8, 30, 14, 6, 6);
        // Flame flash
        if((tick/4)%3==0){
            g.setColor(new Color(255,150,0,120));
            int fx = right ? x+48 : x-14;
            g.fillOval(fx, y+8, 14, 14);
        }
    }

    void drawGoal(Graphics2D g, int x, int y) {
        float pulse = 0.5f + 0.5f*(float)Math.sin(tick*0.07f);
        // Glow
        g.setColor(new Color(80,200,100,(int)(60+40*pulse)));
        g.fillOval(x-20, y-20, GOAL_W+40, GOAL_H+40);
        // Portal ring
        g.setColor(new Color(0,180,80));
        g.setStroke(new BasicStroke(4));
        g.drawOval(x, y, GOAL_W, GOAL_H);
        g.setStroke(new BasicStroke(2));
        g.setColor(new Color(100,255,150));
        g.drawOval(x+5, y+5, GOAL_W-10, GOAL_H-10);
        // Interior
        g.setColor(new Color(50,180,100,(int)(100+60*pulse)));
        g.fillOval(x+8, y+8, GOAL_W-16, GOAL_H-16);
        g.setColor(Color.WHITE);
        g.setFont(new Font("Courier New", Font.BOLD, 10));
        g.drawString("EXIT", x+4, y+GOAL_H/2+4);
        g.setStroke(new BasicStroke(1));
    }

    void drawHUD(Graphics2D g) {
        // Panel
        g.setColor(new Color(0,0,0,150));
        g.fillRoundRect(8,8,200,70,10,10);
        g.setFont(new Font("Courier New", Font.BOLD, 13));
        g.setColor(new Color(100,220,255));
        g.drawString("LEVEL: " + (currentLevel+1) + "/5  — " + getLevelName(), 16, 26);
        g.setColor(new Color(255,220,80));
        g.drawString("SCORE: " + totalScore, 16, 44);
        g.setColor(new Color(255,100,100));
        g.drawString("DEATHS: " + deaths, 16, 62);

        // Controls at bottom
        g.setFont(new Font("Courier New", Font.PLAIN, 11));
        g.setColor(new Color(255,255,255,100));
        g.drawString("A/D: Move   SPACE: Jump   R: Restart Level", W/2-150, H-10);
    }

    String getLevelName() {
        return switch(currentLevel) {
            case 0 -> "Tutorial (lol)";
            case 1 -> "Getting Harder";
            case 2 -> "You Thought";
            case 3 -> "Pure Suffering";
            case 4 -> "The Finale";
            default -> "???";
        };
    }

    // ── DEATH SCREEN ──────────────────────────────────────────
    void drawDeathScreen(Graphics2D g) {
        // Dark overlay
        g.setColor(new Color(0,0,0,160));
        g.fillRect(0,0,W,H);

        // Panel
        int pw = 500, ph = 220;
        int panX = W/2-pw/2, panY = H/2-ph/2;
        g.setColor(new Color(15,5,25,230));
        g.fillRoundRect(panX, panY, pw, ph, 18, 18);
        g.setColor(new Color(200,30,30));
        g.setStroke(new BasicStroke(2));
        g.drawRoundRect(panX, panY, pw, ph, 18, 18);
        g.setStroke(new BasicStroke(1));

        // "FAILURE" tag in big red
        g.setFont(new Font("Courier New", Font.BOLD, 42));
        drawShadowText(g, currentDeathMsg[2],
            W/2 - g.getFontMetrics().stringWidth(currentDeathMsg[2])/2, panY+55,
            new Color(255,60,60), new Color(100,0,0));

        // Main message
        g.setFont(new Font("Courier New", Font.BOLD, 20));
        g.setColor(new Color(240,220,220));
        String msg = currentDeathMsg[0];
        g.drawString(msg, W/2 - g.getFontMetrics().stringWidth(msg)/2, panY+100);

        // Sub message
        g.setFont(new Font("Courier New", Font.ITALIC, 15));
        g.setColor(new Color(180,160,180));
        String sub = currentDeathMsg[1];
        g.drawString(sub, W/2 - g.getFontMetrics().stringWidth(sub)/2, panY+125);

        // Death count shame
        g.setFont(new Font("Courier New", Font.PLAIN, 13));
        g.setColor(new Color(255,120,120));
        String dc = "Total deaths: " + deaths + " (each one is your fault)";
        g.drawString(dc, W/2 - g.getFontMetrics().stringWidth(dc)/2, panY+158);

        // Prompt
        if((tick/20)%2==0) {
            g.setFont(new Font("Courier New", Font.BOLD, 15));
            g.setColor(new Color(255,200,80));
            String pr = "Press R to try again   |   ESC for menu";
            g.drawString(pr, W/2 - g.getFontMetrics().stringWidth(pr)/2, panY+195);
        }

        // Particle remnants still draw underneath (handled in drawGame)
    }

    // ── WIN LEVEL ─────────────────────────────────────────────
    void drawWinLevel(Graphics2D g) {
        g.setColor(new Color(0,0,0,120));
        g.fillRect(0,0,W,H);

        int pw = 440, ph = 160;
        int panX = W/2-pw/2, panY = H/2-ph/2-30;
        g.setColor(new Color(5,25,15,220));
        g.fillRoundRect(panX,panY,pw,ph,15,15);
        g.setColor(new Color(0,200,80));
        g.setStroke(new BasicStroke(2));
        g.drawRoundRect(panX,panY,pw,ph,15,15);
        g.setStroke(new BasicStroke(1));

        g.setFont(new Font("Courier New", Font.BOLD, 34));
        String msg = (currentLevel >= 4) ? "SOMEHOW YOU WON" : "LEVEL CLEAR!";
        drawShadowText(g, msg, W/2-g.getFontMetrics().stringWidth(msg)/2, panY+50,
            new Color(80,255,130), new Color(0,80,40));

        g.setFont(new Font("Courier New", Font.PLAIN, 15));
        g.setColor(new Color(180,240,200));
        String sub = (currentLevel < 4) ?
            "Next level incoming... (it gets worse)" :
            "You actually beat it. Congrats. I'm shocked.";
        g.drawString(sub, W/2-g.getFontMetrics().stringWidth(sub)/2, panY+85);

        // Progress bar
        g.setColor(new Color(0,80,40));
        g.fillRoundRect(panX+20, panY+105, pw-40, 18, 6, 6);
        float prog = (float)winTimer/120;
        g.setColor(new Color(0,200,80));
        g.fillRoundRect(panX+20, panY+105, (int)((pw-40)*prog), 18, 6, 6);
        g.setColor(new Color(255,255,255,150));
        g.setFont(new Font("Courier New", Font.PLAIN, 11));
        g.drawString("Loading next nightmare...", panX+20, panY+118);
    }

    // ── WIN GAME ──────────────────────────────────────────────
    void drawWinGame(Graphics2D g) {
        float t = tick * 0.015f;
        g.setPaint(new GradientPaint(0,0,
            new Color((int)(10+10*Math.sin(t)),30,(int)(10+10*Math.sin(t+2))),
            W,H,new Color(5,(int)(30+15*Math.sin(t+1)),10)));
        g.fillRect(0,0,W,H);

        // Confetti
        for(Particle p : particles) {
            float alpha = (float)p.life/p.maxLife;
            g.setColor(new Color(p.color.getRed(),p.color.getGreen(),p.color.getBlue(),(int)(alpha*255)));
            g.fillRect((int)(p.x-camX/4)-3,(int)p.y-3,6,6);
        }

        g.setFont(new Font("Courier New", Font.BOLD, 52));
        drawShadowText(g, "YOU ESCAPED!", W/2-g.getFontMetrics().stringWidth("YOU ESCAPED!")/2, 130,
            new Color(100,255,140), new Color(0,100,50));

        g.setFont(new Font("Courier New", Font.ITALIC, 18));
        g.setColor(new Color(200,255,210));
        String[] lines = {
            "Against all odds. Against all logic.",
            "Against the very laws of skill.",
            "You, a tiny letter 'i', beat 5 levels of nonsense.",
            "The dot on your head wobbled with pride.",
            "",
            "Final Score: " + totalScore,
            "Total Deaths: " + deaths + "  (" + getRank() + ")"
        };
        for(int i=0;i<lines.length;i++){
            if(lines[i].startsWith("Final") || lines[i].startsWith("Total"))
                g.setColor(new Color(255,230,80));
            else g.setColor(new Color(180,240,195));
            g.drawString(lines[i], W/2-g.getFontMetrics().stringWidth(lines[i])/2, 195+i*32);
        }

        if((tick/25)%2==0){
            g.setFont(new Font("Courier New", Font.BOLD, 16));
            g.setColor(new Color(255,200,80));
            String pr = "Press ESC for main menu";
            g.drawString(pr, W/2-g.getFontMetrics().stringWidth(pr)/2, H-30);
        }

        // Spawn confetti
        if(tick%3==0 && particles.size()<200) {
            particles.add(new Particle(rng.nextInt(W), -10,
                (rng.nextFloat()-0.5f)*2, 2+rng.nextFloat()*3,
                120+rng.nextInt(60),
                new Color(rng.nextInt(255),rng.nextInt(255),rng.nextInt(255))));
        }
        // Keep alive
        Iterator<Particle> it = particles.iterator();
        while(it.hasNext()){
            Particle p = it.next();
            p.x+=p.vx; p.y+=p.vy; p.life--;
            if(p.life<=0) it.remove();
        }
    }

    String getRank() {
        if(deaths == 0)       return "IMPOSSIBLE? Are you cheating?";
        else if(deaths < 5)   return "Suspicious. You sure you're human?";
        else if(deaths < 15)  return "Legitimately impressive";
        else if(deaths < 30)  return "Pretty decent actually";
        else if(deaths < 60)  return "Getting there";
        else if(deaths < 100) return "Persistent";
        else if(deaths < 200) return "...Are you okay?";
        else                  return "Please take a break";
    }

    void drawShadowText(Graphics2D g, String text, int x, int y, Color main, Color shadow) {
        g.setColor(shadow);
        g.drawString(text, x+3, y+3);
        g.setColor(main);
        g.drawString(text, x, y);
    }

    // ══════════════════════════════════════════════════════════
    //  KEY HANDLING
    // ══════════════════════════════════════════════════════════
    @Override
    public void keyPressed(KeyEvent e) {
        int k = e.getKeyCode();

        if(state == State.MENU) {
            if(k==KeyEvent.VK_UP||k==KeyEvent.VK_W)       menuSel = Math.max(0, menuSel-1);
            if(k==KeyEvent.VK_DOWN||k==KeyEvent.VK_S)     menuSel = Math.min(1, menuSel+1);
            if(k==KeyEvent.VK_ENTER||k==KeyEvent.VK_SPACE){
                if(menuSel==0){ currentLevel=0; totalScore=0; deaths=0; startLevel(0); }
                else System.exit(0);
            }
            return;
        }

        if(state == State.WIN_GAME) {
            if(k==KeyEvent.VK_ESCAPE){ state=State.MENU; particles.clear(); }
            return;
        }

        if(k==KeyEvent.VK_A||k==KeyEvent.VK_LEFT)  leftDown=true;
        if(k==KeyEvent.VK_D||k==KeyEvent.VK_RIGHT) rightDown=true;
        if(k==KeyEvent.VK_SPACE||k==KeyEvent.VK_UP||k==KeyEvent.VK_W) jumpDown=true;

        if(k==KeyEvent.VK_R) {
            if(state==State.DEAD || state==State.PLAYING) startLevel(currentLevel);
        }
        if(k==KeyEvent.VK_ESCAPE) {
            state=State.MENU; particles.clear();
            leftDown=rightDown=jumpDown=false;
        }
    }

    @Override
    public void keyReleased(KeyEvent e) {
        int k = e.getKeyCode();
        if(k==KeyEvent.VK_A||k==KeyEvent.VK_LEFT)  leftDown=false;
        if(k==KeyEvent.VK_D||k==KeyEvent.VK_RIGHT) rightDown=false;
        if(k==KeyEvent.VK_SPACE||k==KeyEvent.VK_UP||k==KeyEvent.VK_W) jumpDown=false;
    }

    @Override public void keyTyped(KeyEvent e) {}

    // ── Main ──────────────────────────────────────────────────
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            JFrame frame = new JFrame("i LOST — A Troll Platformer");
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