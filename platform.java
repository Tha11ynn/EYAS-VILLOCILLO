
import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.Random;


public class platform extends JPanel implements ActionListener, KeyListener {

    // ── Window ──────────────────────────────────────────────
    static final int W = 900, H = 550;

    // ── Physics ─────────────────────────────────────────────
    static final float GRAVITY    = 0.55f;
    static final float JUMP_VEL   = -13f;
    static final float MOVE_SPD   = 4f;
    static final float MAX_FALL   = 16f;

    // ── Player ───────────────────────────────────────────────
    float px = 80, py = 300, pvx = 0, pvy = 0;
    boolean onGround = false;
    boolean facingRight = true;
    int playerW = 32, playerH = 48;
    boolean dead = false;
    boolean win  = false;

    // ── Input ────────────────────────────────────────────────
    boolean leftDown, rightDown, jumpDown;
    boolean jumpConsumed = false;

    // ── Scroll ───────────────────────────────────────────────
    float camX = 0;
    static final int LEVEL_W = 4000;

    // ── Platforms ─────────────────────────────────────────────
    static class Platform {
        int x, y, w, h;
        Platform(int x, int y, int w, int h) { this.x=x; this.y=y; this.w=w; this.h=h; }
        Rectangle rect() { return new Rectangle(x, y, w, h); }
    }
    ArrayList<Platform> platforms = new ArrayList<>();

    // ── Zombies ──────────────────────────────────────────────
    static class Zombie {
        float x, y, vx;
        int w = 30, h = 44;
        float left, right;   // patrol bounds
        boolean facingRight;
        int frame = 0;
        int frameTimer = 0;
        boolean alive = true;
        Zombie(float x, float y, float left, float right) {
            this.x=x; this.y=y; this.left=left; this.right=right;
            this.vx = 1.2f; this.facingRight = true;
        }
        Rectangle rect() { return new Rectangle((int)x,(int)y,w,h); }
    }
    ArrayList<Zombie> zombies = new ArrayList<>();

    // ── Coins ────────────────────────────────────────────────
    static class Coin {
        int x, y;
        boolean collected = false;
        Coin(int x, int y){ this.x=x; this.y=y; }
        Rectangle rect(){ return new Rectangle(x-8,y-8,16,16); }
    }
    ArrayList<Coin> coins = new ArrayList<>();
    int score = 0;
    int totalCoins = 0;

    // ── Particles ─────────────────────────────────────────────
    static class Particle {
        float x, y, vx, vy;
        int life, maxLife;
        Color color;
        Particle(float x, float y, float vx, float vy, int life, Color c){
            this.x=x; this.y=y; this.vx=vx; this.vy=vy;
            this.life=life; this.maxLife=life; this.color=c;
        }
    }
    ArrayList<Particle> particles = new ArrayList<>();

    // ── Goal ──────────────────────────────────────────────────
    int goalX = LEVEL_W - 120, goalY = 100, goalW = 50, goalH = 80;

    // ── Misc ──────────────────────────────────────────────────
    Timer timer;
    Random rng = new Random();
    int tick = 0;
    Color SKY_TOP    = new Color(10, 5, 20);
    Color SKY_BOT    = new Color(30, 15, 50);
    Color GROUND_COL = new Color(40, 25, 10);
    Color GRASS_COL  = new Color(30, 80, 30);

    // ── Stars ─────────────────────────────────────────────────
    int[] starX = new int[120], starY = new int[80];

    public platform() {
        setPreferredSize(new Dimension(W, H));
        setBackground(Color.BLACK);
        setFocusable(true);
        addKeyListener(this);

        // Stars
        for(int i=0;i<120;i++) starX[i] = rng.nextInt(LEVEL_W);
        for(int i=0;i<80;i++)  starY[i] = rng.nextInt(H/2);

        buildLevel();

        timer = new Timer(16, this);
        timer.start();
    }

    void buildLevel() {
        platforms.clear(); zombies.clear(); coins.clear();
        score = 0; win = false; dead = false;
        px=80; py=200; pvx=0; pvy=0; camX=0;

        // Ground segments (broken ground look)
        int[] gx = {0,    300,  700,  1050, 1400, 1800, 2100, 2500, 2900, 3200, 3600};
        int[] gw = {280,  380,  330,  330,  380,  280,  380,  380,  280,  380,  400};
        for(int i=0;i<gx.length;i++)
            platforms.add(new Platform(gx[i], H-60, gw[i], 60));

        // Floating platforms
        int[][] fp = {
            {250,380,120,20},  {420,310,100,20},  {580,260,80,20},
            {720,340,130,20},  {900,270,90,20},   {1060,200,110,20},
            {1200,300,100,20}, {1350,240,90,20},  {1500,320,120,20},
            {1680,260,100,20}, {1820,180,90,20},  {1980,300,110,20},
            {2120,240,80,20},  {2280,170,100,20}, {2400,300,120,20},
            {2560,220,90,20},  {2720,160,110,20}, {2880,280,100,20},
            {3050,200,90,20},  {3200,140,110,20}, {3380,260,100,20},
            {3540,180,90,20},  {3700,120,130,20}, {3860,200,120,20}
        };
        for(int[] p : fp) platforms.add(new Platform(p[0],p[1],p[2],p[3]));

        // Zombies on platforms / ground
        int[][] zd = {
            {350,H-104,300,580},   {750,H-104,700,1010},
            {1100,H-104,1050,1380},{600,240,580,800},
            {1700,H-104,1800,2080},{1000,180,900,1150},
            {2050,H-104,2100,2480},{1350,220,1350,1470},
            {2600,H-104,2500,2880},{1700,240,1680,1810},
            {3000,H-104,2900,3180},{2100,220,2120,2270},
            {3400,H-104,3600,3980},{2700,140,2720,2870},
            {3750,H-104,3600,3980},{3300,120,3200,3520}
        };
        for(int[] z : zd) zombies.add(new Zombie(z[0],z[1],z[2],z[3]));

        // Coins
        int[][] cd = {
            {300,360},{440,290},{600,240},{730,320},{920,250},{1080,180},
            {1220,280},{1370,220},{1520,300},{1700,240},{1840,160},{2000,280},
            {2140,220},{2300,150},{2420,280},{2580,200},{2740,140},{2900,260},
            {3070,180},{3220,120},{3400,240},{3560,160},{3720,100},{3880,180},
            {500,H-90},{900,H-90},{1300,H-90},{1700,H-90},{2200,H-90},{2700,H-90},{3300,H-90}
        };
        for(int[] c : cd){ coins.add(new Coin(c[0],c[1])); }
        totalCoins = coins.size();
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        if(!dead && !win) update();
        repaint();
    }

    void update() {
        tick++;

        // ── Player movement ──────────────────────────────────
        if(leftDown)  { pvx = -MOVE_SPD; facingRight = false; }
        else if(rightDown) { pvx = MOVE_SPD; facingRight = true; }
        else pvx = 0;

        if(jumpDown && !jumpConsumed && onGround) {
            pvy = JUMP_VEL;
            jumpConsumed = true;
            spawnJumpDust();
        }
        if(!jumpDown) jumpConsumed = false;

        pvy = Math.min(pvy + GRAVITY, MAX_FALL);
        px += pvx;
        py += pvy;

        // ── Platform collision ───────────────────────────────
        onGround = false;
        Rectangle pr = new Rectangle((int)px,(int)py,playerW,playerH);
        for(Platform p : platforms) {
            Rectangle pl = p.rect();
            if(pr.intersects(pl)) {
                // From above
                if(pvy >= 0 && py+playerH-pvy <= pl.y+4) {
                    py = pl.y - playerH;
                    pvy = 0;
                    onGround = true;
                } else if(pvy <= 0 && py-pvy >= pl.y+pl.height-4) {
                    py = pl.y + pl.height;
                    pvy = 0;
                } else if(pvx > 0) {
                    px = pl.x - playerW;
                } else if(pvx < 0) {
                    px = pl.x + pl.width;
                }
            }
        }

        // Fall death
        if(py > H + 50) die();
        // Left bound
        if(px < 0) px = 0;
        // Right bound
        if(px > LEVEL_W - playerW) px = LEVEL_W - playerW;

        // ── Camera ───────────────────────────────────────────
        float targetCam = px - W/2f + playerW/2f;
        camX += (targetCam - camX) * 0.12f;
        camX = Math.max(0, Math.min(camX, LEVEL_W - W));

        // ── Zombies ──────────────────────────────────────────
        for(Zombie z : zombies) {
            if(!z.alive) continue;
            z.x += z.vx;
            if(z.x < z.left) { z.x = z.left; z.vx = Math.abs(z.vx); z.facingRight=true; }
            if(z.x + z.w > z.right) { z.x = z.right - z.w; z.vx = -Math.abs(z.vx); z.facingRight=false; }

            // Zombie gravity / landing
            float zvy = 2f;
            z.y += zvy;
            Rectangle zr = z.rect();
            for(Platform p : platforms) {
                Rectangle pl = p.rect();
                if(zr.intersects(pl) && z.y+z.h-zvy <= pl.y+6)
                    z.y = pl.y - z.h;
            }

            // Animate
            z.frameTimer++;
            if(z.frameTimer > 10){ z.frame=(z.frame+1)%4; z.frameTimer=0; }

            // Player collision
            Rectangle zrCheck = z.rect();
            Rectangle playerRect = new Rectangle((int)px,(int)py,playerW,playerH);

            if(playerRect.intersects(zrCheck)) {
                // Stomp from above?
                if(pvy > 1 && py + playerH < z.y + z.h*0.6f) {
                    z.alive = false;
                    pvy = JUMP_VEL * 0.7f;
                    score += 100;
                    spawnBloodBurst(z.x + z.w/2f, z.y);
                } else {
                    die();
                }
            }
        }

        // ── Coins ────────────────────────────────────────────
        Rectangle playerRect = new Rectangle((int)px,(int)py,playerW,playerH);
        for(Coin c : coins) {
            if(!c.collected && playerRect.intersects(c.rect())) {
                c.collected = true;
                score += 10;
                spawnCoinPop(c.x, c.y);
            }
        }

        // ── Goal ─────────────────────────────────────────────
        Rectangle goalRect = new Rectangle(goalX, goalY, goalW, goalH);
        if(playerRect.intersects(goalRect)) win = true;

        // ── Particles ────────────────────────────────────────
        Iterator<Particle> it = particles.iterator();
        while(it.hasNext()) {
            Particle p = it.next();
            p.x += p.vx; p.y += p.vy; p.vy += 0.15f; p.life--;
            if(p.life <= 0) it.remove();
        }
    }

    void die() {
        if(!dead) {
            dead = true;
            spawnBloodBurst(px + playerW/2f, py + playerH/2f);
        }
    }

    void spawnBloodBurst(float x, float y) {
        for(int i=0;i<20;i++) {
            float a = (float)(rng.nextDouble()*Math.PI*2);
            float s = 1+rng.nextFloat()*4;
            particles.add(new Particle(x,y,(float)Math.cos(a)*s,(float)Math.sin(a)*s,
                20+rng.nextInt(20), new Color(180+rng.nextInt(60),0,0)));
        }
    }

    void spawnCoinPop(int x, int y) {
        for(int i=0;i<8;i++) {
            float a = (float)(rng.nextDouble()*Math.PI*2);
            particles.add(new Particle(x,y,(float)Math.cos(a)*3,(float)Math.sin(a)*3,
                15, new Color(255,220,0)));
        }
    }

    void spawnJumpDust() {
        for(int i=0;i<6;i++) {
            particles.add(new Particle(px+playerW/2f, py+playerH,
                (rng.nextFloat()-0.5f)*3, -rng.nextFloat()*2,
                12, new Color(180,160,130)));
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

        int cx = (int)camX;

        // ── Sky gradient ─────────────────────────────────────
        GradientPaint sky = new GradientPaint(0,0,SKY_TOP,0,H,SKY_BOT);
        g.setPaint(sky);
        g.fillRect(0,0,W,H);

        // ── Moon ─────────────────────────────────────────────
        int moonX = W - 120 - (int)(cx*0.05f) % W;
        g.setColor(new Color(240,235,200));
        g.fillOval(moonX, 40, 60, 60);
        g.setColor(new Color(220,215,180));
        g.fillOval(moonX+10, 50, 15, 15);
        g.fillOval(moonX+30, 45, 10, 10);

        // ── Stars ────────────────────────────────────────────
        for(int i=0;i<120;i++) {
            int sx = (starX[i] - (int)(cx*0.1f) + LEVEL_W) % W;
            int sy = starY[i % starY.length];
            float bright = 0.5f + 0.5f*(float)Math.sin(tick*0.05f + i);
            int b = (int)(180 + 75*bright);
            g.setColor(new Color(b,b,b));
            int sz = i%5==0?2:1;
            g.fillRect(sx, sy, sz, sz);
        }

        // ── Distant fog silhouettes ──────────────────────────
        drawBgTrees(g, cx);

        // ── Platforms ────────────────────────────────────────
        for(Platform p : platforms) {
            int rx = p.x - cx;
            if(rx+p.w < 0 || rx > W) continue;

            // Dirt
            g.setColor(GROUND_COL);
            g.fillRect(rx, p.y+8, p.w, p.h-8);

            // Grass top
            g.setColor(GRASS_COL);
            g.fillRect(rx, p.y, p.w, 8);

            // Darker grass edge
            g.setColor(new Color(20,60,20));
            g.drawLine(rx, p.y, rx+p.w, p.y);

            // Some dirt texture lines
            g.setColor(new Color(30,18,8));
            for(int tx=rx+15;tx<rx+p.w-10;tx+=20)
                g.drawLine(tx, p.y+12, tx+8, p.y+18);
        }

        // ── Coins ────────────────────────────────────────────
        for(Coin c : coins) {
            if(c.collected) continue;
            int cx2 = c.x - cx;
            if(cx2 < -20 || cx2 > W+20) continue;
            float bob = (float)Math.sin(tick*0.1f + c.x*0.05f)*3;
            drawCoin(g, cx2, (int)(c.y + bob));
        }

        // ── Goal portal ──────────────────────────────────────
        drawGoal(g, goalX - cx, goalY);

        // ── Particles ────────────────────────────────────────
        for(Particle p : particles) {
            int px2 = (int)(p.x - cx);
            float alpha = (float)p.life / p.maxLife;
            Color col = new Color(p.color.getRed(), p.color.getGreen(), p.color.getBlue(),
                (int)(alpha*255));
            g.setColor(col);
            g.fillOval(px2-3, (int)p.y-3, 6, 6);
        }

        // ── Zombies ──────────────────────────────────────────
        for(Zombie z : zombies) {
            if(!z.alive) continue;
            int zx = (int)(z.x - cx);
            if(zx < -60 || zx > W+60) continue;
            drawZombie(g, zx, (int)z.y, z.facingRight, z.frame);
        }

        // ── Player ───────────────────────────────────────────
        if(!dead) {
            int drawX = (int)(px - cx);
            drawPlayer(g, drawX, (int)py, facingRight, onGround ? (tick/6)%4 : 0);
        }

        // ── HUD ──────────────────────────────────────────────
        drawHUD(g);

        // ── Overlays ─────────────────────────────────────────
        if(dead) drawOverlay(g, "YOU DIED!", "Press R to restart", new Color(180,0,0,200));
        if(win)  drawOverlay(g, "YOU ESCAPED!", "Score: "+score+"  Press R to play again", new Color(0,120,60,200));
    }

    void drawBgTrees(Graphics2D g, int cx) {
        // Silhouette dead trees in background
        int[] treeX = {50,180,320,490,640,800,1000,1200,1380,1550};
        for(int i=0;i<treeX.length;i++) {
            int tx = treeX[i] - (int)(cx*0.25f);
            tx = ((tx % W) + W) % W;
            g.setColor(new Color(15,8,25));
            drawDeadTree(g, tx, H-60, 30+i%3*10);
        }
    }

    void drawDeadTree(Graphics2D g, int x, int y, int h) {
        g.setStroke(new BasicStroke(3));
        g.drawLine(x, y, x, y-h);
        // branches
        g.setStroke(new BasicStroke(2));
        g.drawLine(x, y-h*2/3, x-15, y-h*2/3-12);
        g.drawLine(x, y-h*2/3, x+12, y-h*2/3-10);
        g.drawLine(x, y-h/3,   x-10, y-h/3-8);
        g.setStroke(new BasicStroke(1));
    }

    void drawPlayer(Graphics2D g, int x, int y, boolean right, int frame) {
        int flip = right ? 1 : -1;
        int ox = right ? x : x + playerW;

        AffineTransformHelper ath = new AffineTransformHelper(g, ox, y, flip, 1);

        // Legs
        int legOff = frame==1||frame==3 ? 4 : 0;
        g.setColor(new Color(50, 80, 160));
        g.fillRect(2, 32+legOff, 10, 16);
        g.fillRect(14, 32-legOff, 10, 16);

        // Body
        g.setColor(new Color(180, 90, 20));
        g.fillRoundRect(2, 16, 28, 20, 4, 4);

        // Belt
        g.setColor(new Color(80,50,20));
        g.fillRect(2, 28, 28, 4);

        // Arms
        int armSwing = (frame%2==0) ? 4 : -4;
        g.setColor(new Color(200, 150, 100));
        g.fillRect(-4, 18+armSwing, 8, 14);
        g.fillRect(28, 18-armSwing, 8, 14);

        // Head
        g.setColor(new Color(220, 180, 130));
        g.fillRoundRect(6, 0, 22, 20, 6, 6);

        // Hair (messy)
        g.setColor(new Color(60, 30, 10));
        g.fillRect(6, 0, 22, 7);
        g.fillRect(4, 0, 6, 4);

        // Eye
        g.setColor(Color.WHITE);
        g.fillOval(20, 7, 6, 6);
        g.setColor(new Color(30,80,180));
        g.fillOval(22, 8, 4, 4);
        g.setColor(Color.BLACK);
        g.fillOval(23, 9, 2, 2);

        // Weapon (bat/pipe)
        g.setColor(new Color(130, 100, 70));
        g.setStroke(new BasicStroke(3, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.drawLine(30, 20, 44, 8);
        g.setStroke(new BasicStroke(1));

        ath.restore();
    }

    void drawZombie(Graphics2D g, int x, int y, boolean right, int frame) {
        int flip = right ? 1 : -1;
        int ox = right ? x : x + 30;

        AffineTransformHelper ath = new AffineTransformHelper(g, ox, y, flip, 1);

        // Legs (shambling)
        int lo = frame%2==0 ? 5 : -5;
        g.setColor(new Color(30, 70, 30));
        g.fillRect(2,  28+lo, 10, 16);
        g.fillRect(14, 28-lo, 10, 16);

        // Body
        g.setColor(new Color(60, 100, 60));
        g.fillRoundRect(1, 12, 26, 20, 4, 4);

        // Torn shirt details
        g.setColor(new Color(40, 75, 40));
        g.drawLine(5,15,10,28); g.drawLine(18,14,22,27);

        // Arms (outstretched)
        g.setColor(new Color(80, 120, 80));
        int armZ = frame%2==0 ? -4 : 0;
        g.fillRect(-8, 14+armZ, 10, 10);
        g.fillRect(26, 12+armZ, 10, 10);

        // Head
        g.setColor(new Color(100, 140, 80));
        g.fillRoundRect(3, 0, 22, 18, 5, 5);

        // Zombie eyes (red X)
        g.setColor(new Color(220, 30, 30));
        g.setStroke(new BasicStroke(2));
        g.drawLine(6, 5, 10, 9); g.drawLine(10, 5, 6, 9);
        g.drawLine(16, 5, 20, 9); g.drawLine(20, 5, 16, 9);
        g.setStroke(new BasicStroke(1));

        // Mouth
        g.setColor(new Color(160, 20, 20));
        g.drawArc(7, 11, 12, 5, 0, -180);

        // Drool
        g.setColor(new Color(120,200,120,180));
        g.drawLine(12, 14, 12, 18);

        ath.restore();
    }

    void drawCoin(Graphics2D g, int x, int y) {
        // Glow
        g.setColor(new Color(255,200,0,60));
        g.fillOval(x-12, y-12, 24, 24);

        // Coin body
        GradientPaint gold = new GradientPaint(x-8,y-8,new Color(255,230,50),x+8,y+8,new Color(200,140,0));
        g.setPaint(gold);
        g.fillOval(x-8, y-8, 16, 16);

        g.setColor(new Color(255,240,100));
        g.drawString("$", x-4, y+5);
    }

    void drawGoal(Graphics2D g, int x, int y) {
        // Portal glow
        float pulse = 0.5f + 0.5f*(float)Math.sin(tick*0.07f);
        int glowAlpha = (int)(80 + 60*pulse);
        g.setColor(new Color(0, 200, 100, glowAlpha));
        g.fillOval(x-15, y-15, goalW+30, goalH+30);

        // Portal frame
        g.setColor(new Color(0, 160, 80));
        g.setStroke(new BasicStroke(4));
        g.drawOval(x, y, goalW, goalH);
        g.setStroke(new BasicStroke(2));
        g.setColor(new Color(0, 220, 120));
        g.drawOval(x+5, y+5, goalW-10, goalH-10);

        // Portal center shimmer
        g.setColor(new Color(100, 255, 160, (int)(120+80*pulse)));
        g.fillOval(x+8, y+8, goalW-16, goalH-16);

        // Text
        g.setColor(Color.WHITE);
        g.setFont(new Font("Courier New", Font.BOLD, 11));
        g.drawString("EXIT", x+8, y+goalH/2+4);
    }

    void drawHUD(Graphics2D g) {
        // Score panel
        g.setColor(new Color(0,0,0,160));
        g.fillRoundRect(10, 10, 200, 60, 10, 10);
        g.setColor(new Color(0,200,80));
        g.setFont(new Font("Courier New", Font.BOLD, 14));
        g.drawString("SCORE: " + score, 20, 30);
        g.setColor(new Color(255,220,0));
        int collected = (int)coins.stream().filter(c->c.collected).count();
        g.drawString("COINS: " + collected + "/" + totalCoins, 20, 50);

        // Zombie kill count
        int killed = (int)zombies.stream().filter(z->!z.alive).count();
        g.setColor(new Color(220,60,60));
        g.drawString("KILLS: " + killed, 20, 65);

        // Controls hint
        g.setColor(new Color(255,255,255,120));
        g.setFont(new Font("Courier New", Font.PLAIN, 11));
        g.drawString("A/D or ←/→  SPACE:Jump  STOMP zombies!", W/2-130, H-10);
    }

    void drawOverlay(Graphics2D g, String title, String sub, Color bg) {
        g.setColor(bg);
        g.fillRect(0, 0, W, H);

        g.setColor(Color.WHITE);
        g.setFont(new Font("Courier New", Font.BOLD, 56));
        FontMetrics fm = g.getFontMetrics();
        int tw = fm.stringWidth(title);
        g.drawString(title, W/2 - tw/2, H/2 - 20);

        g.setFont(new Font("Courier New", Font.PLAIN, 22));
        fm = g.getFontMetrics();
        int sw = fm.stringWidth(sub);
        g.setColor(new Color(220,220,220));
        g.drawString(sub, W/2 - sw/2, H/2 + 30);
    }

    // ── Key handling ─────────────────────────────────────────
    @Override public void keyPressed(KeyEvent e) {
        int k = e.getKeyCode();
        if(k==KeyEvent.VK_A||k==KeyEvent.VK_LEFT)  leftDown=true;
        if(k==KeyEvent.VK_D||k==KeyEvent.VK_RIGHT) rightDown=true;
        if(k==KeyEvent.VK_SPACE||k==KeyEvent.VK_UP||k==KeyEvent.VK_W) jumpDown=true;
        if(k==KeyEvent.VK_R) buildLevel();
    }
    @Override public void keyReleased(KeyEvent e) {
        int k = e.getKeyCode();
        if(k==KeyEvent.VK_A||k==KeyEvent.VK_LEFT)  leftDown=false;
        if(k==KeyEvent.VK_D||k==KeyEvent.VK_RIGHT) rightDown=false;
        if(k==KeyEvent.VK_SPACE||k==KeyEvent.VK_UP||k==KeyEvent.VK_W) jumpDown=false;
    }
    @Override public void keyTyped(KeyEvent e) {}

    // ── Helper: flip transform ────────────────────────────────
    static class AffineTransformHelper {
        Graphics2D g;
        java.awt.geom.AffineTransform saved;
        AffineTransformHelper(Graphics2D g, int ox, int oy, int flipX, int flipY) {
            this.g = g;
            saved = g.getTransform();
            g.translate(ox, oy);
            g.scale(flipX, flipY);
        }
        void restore() { g.setTransform(saved); }
    }

    // ── Main ──────────────────────────────────────────────────
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            JFrame frame = new JFrame("Zombie Platformer — She Was a Zombie!");
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