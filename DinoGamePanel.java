package me.shaoxia;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.geom.AffineTransform;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

/**
 * 拯救公猪游戏

 */
public class DinoGamePanel extends JPanel implements ActionListener {

    // --- 游戏状态变量 ---
    private static final int HEIGHT = 260;
    private static final int GROUND_Y = 210;
    private Timer timer;
    private Random rand = new Random();

    // 多线程相关
    private volatile boolean running = true;
    private Thread logicThread;
    private final Object logicLock = new Object();

    // 核心状态开关
    private boolean isPrologue = true;
    private long prologueStartTime = 0;
    private boolean isPaused = false;
    private boolean isEndlessMode = false;

    private boolean isStarted = false;
    private boolean isOver = false;
    private boolean isBossMode = false;

    // 胜利/死亡动画相关
    private boolean isVictory = false;
    private long victoryStartTime = 0;
    private boolean thiefCrashed = false;

    // --- 速度与分数变量 ---
    private double gameSpeed = 7.0;
    private final double ACCELERATION = 0.0018;
    private final double MAX_SPEED = 26.0;
    private double score = 0;
    private int screenShake = 0;

    // 布局变量：骑士位置
    private static final int DINO_X = 320;

    // --- 生命周期与护盾系统 ---
    private int lives = 3;
    private boolean isShieldActive = false;
    private long shieldStartTime = 0;
    private long lastShieldUseTime = -99999;
    private static final long SHIELD_COOLDOWN = 30000;
    private long hitInvincibilityTime = 0;

    // 动态距离变量
    private double currentOrcOffset = 350;
    private double currentThiefOffset = -250;

    // --- 物理逻辑 & 实体高度 ---
    private double dinoY = GROUND_Y - 44;
    private double dinoVelY = 0;
    private boolean isBlockingRight = false;
    private boolean isBlockingLeft = false;

    private double orcY = GROUND_Y - 44;
    private double orcVelY = 0;

    private double thiefY = GROUND_Y - 40;
    private double thiefVelY = 0;
    private int thiefThrowTimer = 0;

    // --- 集合 ---
    private List<Obstacle> obstacles = new ArrayList<>();
    private List<DustParticle> dust = new ArrayList<>();
    private List<Point> clouds = new ArrayList<>();
    private List<Bomb> bombs = new ArrayList<>();
    private List<Dagger> daggers = new ArrayList<>();

    private int bombCooldown = 0;
    private int daggerCooldown = 0;

    public DinoGamePanel() {
        setPreferredSize(new Dimension(800, HEIGHT));
        setDoubleBuffered(true);
        setOpaque(false);
        setFocusable(true);
        for (int i = 0; i < 6; i++) clouds.add(new Point(rand.nextInt(1500), 30 + rand.nextInt(50)));

        addKeyListener(new KeyAdapter() {
            public void keyPressed(KeyEvent e) {
                int code = e.getKeyCode();

                if (isBossMode && code == KeyEvent.VK_ESCAPE) {
                    isBossMode = false;
                    isPaused = true;
                    repaint();
                    return;
                }

                if (code == KeyEvent.VK_ESCAPE) {
                    if (isVictory && (System.currentTimeMillis() - victoryStartTime > 10000)) {
                        isBossMode = true;
                        isPaused = true;
                    } else if (!isPrologue) {
                        isPaused = !isPaused;
                    }
                    repaint();
                    return;
                }

                if (isPaused) {
                    if (code == KeyEvent.VK_1) {
                        isBossMode = true;
                        isPaused = false;
                    } else if (code == KeyEvent.VK_2) {
                        isEndlessMode = true;
                        isPaused = false;
                        isPrologue = false;
                        resetGame();
                    } else if (code == KeyEvent.VK_3) {
                        isEndlessMode = false;
                        isPaused = false;
                        isPrologue = false;
                        resetGame();
                    }
                    repaint();
                    return;
                }

                if (code == KeyEvent.VK_SPACE || code == KeyEvent.VK_UP) {
                    if (isPrologue) {
                        long elapsed = System.currentTimeMillis() - prologueStartTime;
                        if (elapsed > 8500) {
                            isPrologue = false;
                            isEndlessMode = false;
                            resetGame();
                        }
                    } else if (isVictory) {
                        long elapsed = System.currentTimeMillis() - victoryStartTime;
                        if (elapsed > 10000) {
                            isEndlessMode = true;
                            isPrologue = false;
                            isVictory = false;
                            resetGame();
                        }
                    } else if (isOver || !isStarted) {
                        resetGame();
                    } else if (dinoY >= GROUND_Y - 45) {
                        dinoVelY = -15.5;
                        createDust(DINO_X + 10);
                    }
                }

                if (!isOver && isStarted && !isBossMode && !isPrologue && !isPaused) {
                    if (code == KeyEvent.VK_RIGHT) { isBlockingRight = true; isBlockingLeft = false; }
                    if (code == KeyEvent.VK_LEFT) { isBlockingLeft = true; isBlockingRight = false; }

                    if (code == KeyEvent.VK_DOWN) {
                        if (!isShieldActive && System.currentTimeMillis() - lastShieldUseTime > SHIELD_COOLDOWN) {
                            isShieldActive = true;
                            shieldStartTime = System.currentTimeMillis();
                            lastShieldUseTime = System.currentTimeMillis();
                        }
                    }
                }
            }

            public void keyReleased(KeyEvent e) {
                int code = e.getKeyCode();
                if (isVictory) return;
                if (code == KeyEvent.VK_RIGHT) isBlockingRight = false;
                if (code == KeyEvent.VK_LEFT) isBlockingLeft = false;
            }
        });

        startLogicThread();
        timer = new Timer(16, this);
        timer.start();
    }

    private void resetGame() {
        score = 0;
        lives = 3;
        isShieldActive = false;
        shieldStartTime = 0;
        lastShieldUseTime = -99999;
        hitInvincibilityTime = 0;
        thiefCrashed = false;

        gameSpeed = 7.0;
        isStarted = true;
        isOver = false;
        isBossMode = false;
        isVictory = false;
        victoryStartTime = 0;
        dinoY = GROUND_Y - 44;
        dinoVelY = 0;
        orcY = GROUND_Y - 44;
        orcVelY = 0;
        thiefY = GROUND_Y - 40;
        thiefVelY = 0;
        thiefThrowTimer = 0;

        currentOrcOffset = 350;
        currentThiefOffset = -250;

        obstacles.clear();
        dust.clear();
        bombs.clear();
        daggers.clear();
        bombCooldown = 120;
        daggerCooldown = 60;
        requestFocusInWindow();
    }

    private void startLogicThread() {
        logicThread = new Thread(() -> {
            while (running) {
                long startTime = System.currentTimeMillis();
                if (isStarted && !isOver && !isBossMode && !isPrologue && !isPaused) {
                    synchronized (logicLock) {
                        runBackgroundLogic();
                    }
                }
                long sleepTime = 16 - (System.currentTimeMillis() - startTime);
                if (sleepTime > 0) { try { Thread.sleep(sleepTime); } catch (InterruptedException e) { break; } }
            }
        }, "GameLogicThread");
        logicThread.setPriority(Thread.MAX_PRIORITY);
        logicThread.start();
    }

    private void runBackgroundLogic() {
        long t = System.currentTimeMillis();

        if (gameSpeed < MAX_SPEED) gameSpeed += ACCELERATION;
        score += (gameSpeed / 42.0);

        if (thiefThrowTimer > 0) thiefThrowTimer--;

        if (isShieldActive && (t - shieldStartTime > 10000)) {
            isShieldActive = false;
        }

        if (!isVictory) {
            if (isEndlessMode) {
                if (!thiefCrashed) currentThiefOffset = -220 + Math.cos(t / 600.0) * 50;
                currentOrcOffset = 320 + Math.sin(t / 800.0) * 60;
            } else {
                if (score < 500 && !thiefCrashed) {
                    currentThiefOffset = -220 + Math.cos(t / 600.0) * 50;
                } else if (score >= 500 && !thiefCrashed) {
                    double thiefRealX = DINO_X + currentThiefOffset;
                    for (Obstacle ob : obstacles) {
                        if (ob.x > thiefRealX && ob.x < thiefRealX + 150) {
                            currentThiefOffset += 18;
                            thiefRealX = DINO_X + currentThiefOffset;
                            if (thiefRealX + 15 >= ob.x) {
                                thiefCrashed = true;
                                ob.hasCrashedThief = true;
                                createDust((int)ob.x);
                            }
                            break;
                        }
                    }
                }

                if (score < 1000) {
                    currentOrcOffset = 320 + Math.sin(t / 800.0) * 60;
                } else {
                    double orcRealX = DINO_X + currentOrcOffset;
                    Obstacle target = null;
                    for (Obstacle ob : obstacles) {
                        if (ob.x > orcRealX) { target = ob; break; }
                    }
                    if (target == null) {
                        target = new Obstacle(getWidth() + 100, GROUND_Y - 60);
                        obstacles.add(target);
                    }

                    currentOrcOffset += 20;
                    orcRealX = DINO_X + currentOrcOffset;
                    if (orcRealX + 30 >= target.x) {
                        isVictory = true;
                        isOver = true;
                        victoryStartTime = System.currentTimeMillis();

                        // 强制重置骑士高度为地面，防止滞空相拥
                        dinoY = GROUND_Y - 44;
                        dinoVelY = 0;

                        createDust((int)target.x);
                        bombs.clear();
                        daggers.clear();
                    }
                }
            }
        }

        updatePhysics();
        handleAIAndEnemies();
        handleCollisions();

        if (screenShake > 0) screenShake--;
    }

    public void stopGame() {
        running = false;
        if (logicThread != null) logicThread.interrupt();
    }

    private void createDust(int x) {
        for (int i = 0; i < 6; i++) dust.add(new DustParticle(x, GROUND_Y));
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        if (isBossMode) return;
        repaint();
    }

    private void updatePhysics() {
        dinoY += dinoVelY;
        if (dinoY < GROUND_Y - 44) dinoVelY += 0.88;
        else {
            if (dinoVelY > 5) { screenShake = 6; createDust(DINO_X + 10); }
            dinoY = GROUND_Y - 44;
            dinoVelY = 0;
        }

        orcY += orcVelY;
        if (orcY < GROUND_Y - 44) orcVelY += 0.88; else { orcY = GROUND_Y - 44; orcVelY = 0; }

        if (!thiefCrashed) {
            thiefY += thiefVelY;
            if (thiefY < GROUND_Y - 40) thiefVelY += 0.88; else { thiefY = GROUND_Y - 40; thiefVelY = 0; }
        }
    }

    private void handleAIAndEnemies() {
        double orcRealX = DINO_X + currentOrcOffset;
        double thiefRealX = DINO_X + currentThiefOffset;

        if (isEndlessMode || score < 1000) {
            if (bombCooldown > 0) bombCooldown--;
            if (bombCooldown <= 0 && rand.nextInt(100) < 2) {
                bombs.add(new Bomb((int) orcRealX, (int) orcY));
                bombCooldown = 90 + rand.nextInt(100);
            }
            if (isEndlessMode || score < 950) {
                for (Obstacle ob : obstacles) {
                    if (ob.x > orcRealX && ob.x < orcRealX + 140 && orcY >= GROUND_Y - 45) {
                        orcVelY = -15; break;
                    }
                }
            }
        }

        if ((isEndlessMode || score > 100) && !thiefCrashed) {
            if ((isEndlessMode || score < 470) && thiefY >= GROUND_Y - 40 && rand.nextInt(100) < 2) {
                thiefVelY = -15.0;
            }
            if (daggerCooldown > 0) daggerCooldown--;
            if (daggerCooldown <= 0 && rand.nextInt(100) < 3) {
                daggers.add(new Dagger((int) thiefRealX + 20, (int) thiefY + 12));
                daggerCooldown = 60 + rand.nextInt(70);
                thiefThrowTimer = 15;
            }
        }
    }

    private void takeDamage() {
        if (isShieldActive) return;
        if (System.currentTimeMillis() - hitInvincibilityTime < 2000) return;

        lives--;
        if (lives <= 0) {
            isOver = true;
            screenShake = 20;
        } else {
            hitInvincibilityTime = System.currentTimeMillis();
            screenShake = 15;
        }
    }

    private void handleCollisions() {
        Rectangle dinoHitbox = new Rectangle(DINO_X + 12, (int) dinoY - 10, 20, 48);

        Iterator<Obstacle> obIter = obstacles.iterator();
        while (obIter.hasNext()) {
            Obstacle ob = obIter.next();
            if (!isVictory) ob.x -= gameSpeed;

            if (ob.getBounds().intersects(dinoHitbox)) {
                if (isShieldActive) {
                    if (!ob.isKnockedDown) {
                        ob.isKnockedDown = true;
                        score += 15;
                        screenShake = 5;
                        createDust((int)ob.x);
                    }
                } else {
                    if (!ob.isKnockedDown) takeDamage();
                }
            }
            if (ob.x < -100) obIter.remove();
        }

        Iterator<Bomb> bIter = bombs.iterator();
        while (bIter.hasNext()) {
            Bomb b = bIter.next();
            if (b.update(gameSpeed)) { bIter.remove(); continue; }
            if (b.getBounds().intersects(dinoHitbox)) {
                if (isShieldActive || isBlockingRight) { bIter.remove(); score += 10; screenShake = 3; }
                else { takeDamage(); bIter.remove(); }
            }
        }

        Iterator<Dagger> dIter = daggers.iterator();
        while (dIter.hasNext()) {
            Dagger d = dIter.next();
            if (d.update(gameSpeed)) { dIter.remove(); continue; }
            if (d.getBounds().intersects(dinoHitbox)) {
                if (isShieldActive || isBlockingLeft) { dIter.remove(); score += 15; screenShake = 2; }
                else { takeDamage(); dIter.remove(); }
            }
        }

        for (Point p : clouds) {
            if (!isVictory) p.x -= (gameSpeed * 0.15);
            if (p.x < -150) { p.x = getWidth() + rand.nextInt(300); p.y = 30 + rand.nextInt(50); }
        }

        if (!isVictory && rand.nextInt(100) < 5 && (obstacles.isEmpty() || obstacles.get(obstacles.size() - 1).x < getWidth() - 350)) {
            obstacles.add(new Obstacle(getWidth(), GROUND_Y - 60));
        }

        for (int i = 0; i < dust.size(); i++) { if (dust.get(i).update()) dust.remove(i--); }
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        if (isBossMode) return;

        Graphics2D g2 = (Graphics2D) g;
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        if (screenShake > 0) {
            g2.translate(rand.nextInt(screenShake) - screenShake / 2, rand.nextInt(screenShake) - screenShake / 2);
        }

        drawFancyClouds(g2);
        g2.setColor(new Color(120, 120, 120, 80));
        g2.drawLine(0, GROUND_Y, getWidth(), GROUND_Y);
        for (Obstacle ob : obstacles) ob.render(g2);

        if (!isPrologue) {
            for (DustParticle p : dust) p.draw(g2);
            for (Bomb b : bombs) b.render(g2);
            for (Dagger d : daggers) d.render(g2);

            if (isVictory) {
                long elapsed = System.currentTimeMillis() - victoryStartTime;
                drawVictoryCutsceneForeground(g2, elapsed);
            } else if (!isOver) {
                boolean drawKnight = true;
                if (!isVictory && (System.currentTimeMillis() - hitInvincibilityTime < 2000)) {
                    if ((System.currentTimeMillis() % 200) < 100) drawKnight = false;
                }
                if (drawKnight) drawDino(g2, DINO_X, (int) dinoY, isShieldActive);

                if (isShieldActive) {
                    long elapsed = System.currentTimeMillis() - shieldStartTime;
                    boolean drawShield = true;
                    if (elapsed > 7000) {
                        int cycle = 500;
                        if (elapsed > 8500) cycle = 250;
                        if (elapsed > 9500) cycle = 100;
                        if ((elapsed % cycle) < (cycle / 2)) drawShield = false;
                    }
                    if (drawShield) {
                        double angle = (elapsed % 300) / 300.0 * Math.PI * 2;
                        int radius = 40;
                        int cx = DINO_X + 15 + (int)(Math.cos(angle) * radius);
                        int cy = (int)dinoY + 10 + (int)(Math.sin(angle) * radius);
                        AffineTransform oldTx = g2.getTransform();
                        g2.translate(cx, cy); g2.rotate(angle + Math.PI / 2);
                        drawVividShield(g2, -10, -15);
                        g2.setTransform(oldTx);
                    }
                }

                if (isEndlessMode || score < 1000) drawOrcWithPig(g2, DINO_X + (int)currentOrcOffset, (int) orcY);
                if ((isEndlessMode || score > 100) && !thiefCrashed) drawThief(g2, DINO_X + (int)currentThiefOffset, (int) thiefY);
            }

            if (!isVictory) {
                for (int i = 0; i < 3; i++) drawUIHeart(g2, 30 + i * 35, 30, i < lives);
                g2.setFont(new Font("Monospaced", Font.BOLD, 14));
                long timeSinceLastShield = System.currentTimeMillis() - lastShieldUseTime;
                if (timeSinceLastShield < SHIELD_COOLDOWN) {
                    int cd = (int)((SHIELD_COOLDOWN - timeSinceLastShield) / 1000);
                    g2.setColor(new Color(180, 180, 180));
                    g2.drawString("SHIELD CD: " + cd + "s", 20, 65);
                } else {
                    g2.setColor(new Color(255, 255, 255));
                    g2.drawString("SHIELD READY [DOWN]", 20, 65);
                }

                if (isEndlessMode) {
                    g2.setColor(new Color(255, 100, 100));
                    g2.drawString("ENDLESS MODE", getWidth() - 180, 25);
                }
                g2.setColor(Color.WHITE);
                g2.setFont(new Font("Monospaced", Font.BOLD, 18));
                g2.drawString(String.format("SCORE: %05d", (int) score), getWidth() - 180, 45);
            }

            if (isOver && !isVictory) {
                g2.setColor(new Color(0, 0, 0, 160)); g2.fillRect(0, 0, getWidth(), HEIGHT);
                g2.setColor(Color.WHITE); g2.setFont(new Font("Monospaced", Font.BOLD, 18));
                g2.drawString("MISSION FAILED - PRESS SPACE", getWidth() / 2 - 140, HEIGHT / 2);
            }
        }

        if (isPrologue) drawPrologue(g2);
        if (isPaused) drawPauseMenu(g2);

        Toolkit.getDefaultToolkit().sync();
    }

    private void drawPrologue(Graphics2D g2) {
        if (prologueStartTime == 0) prologueStartTime = System.currentTimeMillis();
        long elapsed = System.currentTimeMillis() - prologueStartTime;

        g2.setColor(new Color(15, 15, 25, 200));
        g2.fillRect(0, 0, getWidth(), HEIGHT);

        g2.setFont(new Font("微软雅黑", Font.BOLD, 18));
        int startY = 60;
        Color textColor = new Color(150, 150, 150);

        if (elapsed > 500) {
            g2.setColor(textColor);
            g2.drawString("【一个月黑风高的夜晚】", 60, startY);
            drawPixelIcon(g2, "MOON", 300, startY - 15);
        }
        if (elapsed > 2500) {
            g2.setColor(textColor);
            g2.drawString("小偷施展了魔法，把公主变成了一头猪，扛着就跑！", 60, startY + 40);
            drawPixelIcon(g2, "MASKED_HEAD", 500, startY + 20);
        }
        if (elapsed > 4500) {
            g2.setColor(textColor);
            g2.drawString("盗贼闻讯赶来，伺机阻挠...", 60, startY + 80);
            drawPixelIcon(g2, "THIEF", 300, startY + 65);
        }
        if (elapsed > 6500) {
            g2.setColor(textColor);
            g2.drawString("骑士来不及拿宝剑，只带上盾牌，轻装上阵开始追击！", 60, startY + 120);
            drawPixelIcon(g2, "CROSS_SHIELD", 510, startY + 115);
        }
        if (elapsed > 8500) {
            long blink = elapsed % 1000;
            if (blink > 500) {
                g2.setColor(new Color(255, 210, 40));
                g2.setFont(new Font("微软雅黑", Font.PLAIN, 16));
                g2.drawString("- 按 SPACE 键开启追击 -", getWidth()/2 - 90, HEIGHT - 20);
            }
        }
    }

    private void drawPauseMenu(Graphics2D g2) {
        g2.setColor(new Color(0, 0, 0, 210));
        g2.fillRect(0, 0, getWidth(), HEIGHT);
        g2.setColor(Color.WHITE);
        g2.setFont(new Font("微软雅黑", Font.BOLD, 22));
        g2.drawString("【 游戏暂停 / 模式选择 】", getWidth() / 2 - 130, 70);

        g2.setFont(new Font("微软雅黑", Font.PLAIN, 16));
        g2.setColor(new Color(255, 210, 40));
        g2.drawString("[ 1 ] 启动老板键 (完全隐藏游戏进程)", getWidth() / 2 - 140, 115);
        g2.drawString("[ 2 ] 开启无限模式 (无尽追逐，不惧结局)", getWidth() / 2 - 140, 150);
        g2.drawString("[ 3 ] 重新开始剧情模式", getWidth() / 2 - 140, 185);

        g2.setColor(new Color(150, 150, 150));
        g2.drawString("[ ESC ] 恢复当前游戏", getWidth() / 2 - 140, 230);
    }

    private void drawPixelIcon(Graphics2D g2, String type, int x, int y) {
        if (type.equals("MOON")) {
            g2.setColor(new Color(255, 255, 180));
            g2.fillOval(x, y, 16, 16);
            g2.setColor(new Color(15, 15, 25, 200));
            g2.fillOval(x + 4, y - 2, 16, 16);
        } else if (type.equals("MASKED_HEAD")) {
            g2.setColor(new Color(20, 20, 20)); g2.fillRect(x, y, 16, 16);
            g2.setColor(new Color(240, 190, 150)); g2.fillRect(x + 4, y + 6, 8, 4);
            g2.setColor(Color.BLACK); g2.fillRect(x + 5, y + 7, 2, 2); g2.fillRect(x + 9, y + 7, 2, 2);
        } else if (type.equals("THIEF")) {
            g2.setColor(new Color(40, 60, 50)); g2.fillRect(x, y-5, 15, 10);
            g2.setColor(new Color(240, 190, 150)); g2.fillRect(x, y+5, 15, 8);
            g2.setColor(Color.BLACK); g2.fillRect(x+3, y+7, 2, 2); g2.fillRect(x+10, y+7, 2, 2);
        } else if (type.equals("CROSS_SHIELD")) {
            drawVividShield(g2, x, y - 10);
        }
    }

    private void drawUIHeart(Graphics2D g2, int x, int y, boolean active) {
        int p = 2;
        if (active) g2.setColor(new Color(255, 50, 80));
        else g2.setColor(new Color(100, 100, 100, 150));
        g2.fillRect(x - 2 * p, y - 2 * p, 2 * p, 2 * p); g2.fillRect(x + 2 * p, y - 2 * p, 2 * p, 2 * p);
        g2.fillRect(x - 3 * p, y - p, 8 * p, 3 * p); g2.fillRect(x - 2 * p, y + 2 * p, 6 * p, 2 * p);
        g2.fillRect(x - p, y + 4 * p, 4 * p, 2 * p); g2.fillRect(x, y + 6 * p, 2 * p, 2 * p);
    }

    private void drawAlphaHeart(Graphics2D g2, int x, int y, int alpha) {
        g2.setColor(new Color(255, 50, 80, alpha));
        int p = 2;
        g2.fillRect(x - 2 * p, y - 2 * p, 2 * p, 2 * p); g2.fillRect(x + 2 * p, y - 2 * p, 2 * p, 2 * p);
        g2.fillRect(x - 3 * p, y - p, 8 * p, 3 * p); g2.fillRect(x - 2 * p, y + 2 * p, 6 * p, 2 * p);
        g2.fillRect(x - p, y + 4 * p, 4 * p, 2 * p); g2.fillRect(x, y + 6 * p, 2 * p, 2 * p);
    }

    private void drawVictoryCutsceneForeground(Graphics2D g2, long elapsed) {
        Obstacle crashTree = obstacles.isEmpty() ? null : obstacles.get(obstacles.size()-1);
        int crashX = crashTree != null ? (int)crashTree.x : getWidth() - 150;

        int pigLandX = crashX - 70;
        int pigLandY = GROUND_Y - 5;
        int pigX = pigLandX;
        int pigY = pigLandY;

        int knightX = DINO_X;
        if (elapsed > 2000 && elapsed < 6500) {
            double progress = (elapsed - 2000) / 4500.0;
            knightX = DINO_X + (int)(progress * (pigX - 16 - DINO_X));
        } else if (elapsed >= 6500) {
            knightX = pigX - 16;
        }

        boolean hideShield = (elapsed >= 6000);
        drawDino(g2, knightX, (int)dinoY, hideShield);

        // 【关键修复】结局中调用黑衣小偷（背猪者）的装死眩晕特效
        drawDeadBlackThiefBody(g2, crashX - 10, GROUND_Y - 5);

        if (elapsed < 1000) {
            double t = elapsed / 1000.0;
            pigX = crashX - (int)(t * 70);
            pigY = (GROUND_Y - 50) - (int)(Math.sin(t * Math.PI) * 40);
        }

        if (elapsed < 4500) {
            drawStandalonePig(g2, pigX, pigY - 25);
        } else {
            drawMonkeyLeftFacing(g2, pigLandX, pigLandY);
        }

        // 绘制拥抱时的双臂交叉重叠
        if (elapsed >= 6500) {
            int p = 3;
            Color armor = new Color(180, 180, 190);
            Color fur = new Color(100, 50, 10);

            g2.setColor(fur);
            g2.fillRect(pigLandX + 2*p, pigLandY - 7*p, -5*p, 2*p);
            g2.fillRect(pigLandX - 3*p, pigLandY - 7*p, 2*p, 3*p);

            g2.setColor(armor);
            g2.fillRect(knightX + 5*p, (int)dinoY - 1*p, 6*p, 2*p);
            g2.fillRect(knightX + 11*p, (int)dinoY - 1*p, 2*p, 3*p);
        }

        if (elapsed > 3500 && elapsed < 5000) {
            Random magicRand = new Random(elapsed / 80);
            for (int i = 0; i < 25; i++) {
                int sx = pigLandX + magicRand.nextInt(60) - 10;
                int sy = pigLandY - magicRand.nextInt(90);
                g2.setColor(magicRand.nextBoolean() ? Color.WHITE : new Color(255, 230, 100));
                g2.fillRect(sx, sy, 4, 4);
            }
        }

        if (elapsed >= 4500 && elapsed <= 5500) {
            int flashAlpha = 0;
            if (elapsed < 5000) flashAlpha = (int) ((elapsed - 4500) * 255 / 500);
            else flashAlpha = (int) ((5500 - elapsed) * 255 / 500);
            flashAlpha = Math.max(0, Math.min(255, flashAlpha));
            if (flashAlpha > 0) {
                g2.setColor(new Color(255, 255, 255, flashAlpha));
                g2.fillRect(0, 0, getWidth(), HEIGHT);
            }
        }

        // 连续升起的正弦波爱心
        if (elapsed > 6500) {
            long embraceTime = elapsed - 6500;
            int maxHearts = (int) (embraceTime / 300);
            for (int i = 0; i <= maxHearts; i++) {
                long lifeTime = embraceTime - (i * 300L);
                if (lifeTime < 2500) {
                    int floatUp = (int) (lifeTime / 15);
                    int wobble = (int) (Math.sin(lifeTime / 150.0) * 15);
                    int alpha = 255 - (int) (lifeTime * 255 / 2500);
                    drawAlphaHeart(g2, knightX + 18 + wobble, pigLandY - 30 - floatUp, Math.max(0, alpha));
                }
            }
        }

        if (elapsed > 8000) {
            int textAlpha = (int) Math.min(255, (elapsed - 8000) / 10);
            g2.setColor(new Color(180, 20, 60, textAlpha));
            g2.setFont(new Font("微软雅黑", Font.BOLD, 22));
            String text1 = "魔法解除！被抓走的公猪原来是一只猿猴！";
            String text2 = "骑士和猿猴从此幸福地相拥在一起~";
            FontMetrics fm = g2.getFontMetrics();
            g2.drawString(text1, getWidth() / 2 - fm.stringWidth(text1) / 2, HEIGHT / 2 - 70);
            g2.drawString(text2, getWidth() / 2 - fm.stringWidth(text2) / 2, HEIGHT / 2 - 40);

            if (elapsed > 10000) {
                g2.setFont(new Font("微软雅黑", Font.PLAIN, 16));
                g2.setColor(new Color(150, 150, 150, textAlpha));
                String text3 = "- 按 SPACE 键开启无限模式，或按 ESC 隐藏 -";
                fm = g2.getFontMetrics();
                g2.drawString(text3, getWidth() / 2 - fm.stringWidth(text3) / 2, HEIGHT / 2 + 30);
            }
        }
    }

    private void drawStandalonePig(Graphics2D g2, int px, int py) {
        int p = 5; py = py - 7 * p + 5;
        Color pigPink = new Color(255, 170, 190); Color pigDark = new Color(220, 130, 150);
        Color crownGold = new Color(255, 200, 0);
        g2.setColor(pigDark); drawRect(g2, px, py + 2 * p, 16 * p, 10 * p, p);
        g2.setColor(pigPink); drawRect(g2, px + p, py + p, 14 * p, 10 * p, p);
        g2.setColor(new Color(255, 110, 140)); drawRect(g2, px - 2 * p, py + 5 * p, 4 * p, 4 * p, p);
        g2.setColor(new Color(60, 20, 30)); drawRect(g2, px - p, py + 6 * p, p, 2 * p, p);
        g2.setColor(Color.WHITE); drawRect(g2, px + 3 * p, py + 3 * p, 2 * p, 2 * p, p); drawRect(g2, px + 8 * p, py + 3 * p, 2 * p, 2 * p, p);
        g2.setColor(Color.BLACK); drawRect(g2, px + 4 * p, py + 3 * p, p, p, p);
        g2.setColor(crownGold); drawRect(g2, px + 6 * p, py - 2 * p, 5 * p, p, p); drawRect(g2, px + 6 * p, py - 3 * p, p, p, p);
        drawRect(g2, px + 8 * p, py - 4 * p, p, 2 * p, p); drawRect(g2, px + 10 * p, py - 3 * p, p, p, p);
    }

    private void drawMonkeyLeftFacing(Graphics2D g2, int x, int y) {
        int p = 3;
        Color fur = new Color(139, 69, 19);
        Color skin = new Color(222, 184, 135);

        g2.setColor(fur); g2.fillRect(x + 2*p, y - 3*p, 2*p, 3*p); g2.fillRect(x + 6*p, y - 3*p, 2*p, 3*p);
        g2.fillRect(x + 2*p, y - 10*p, 6*p, 7*p);
        g2.setColor(skin); g2.fillRect(x + 3*p, y - 8*p, 3*p, 4*p);
        g2.setColor(fur); g2.fillRect(x + 1*p, y - 16*p, 8*p, 6*p);
        g2.setColor(skin); g2.fillRect(x + 1*p, y - 15*p, 6*p, 4*p);
        g2.setColor(Color.BLACK); g2.fillRect(x + 2*p, y - 14*p, p, p); g2.fillRect(x + 4*p, y - 14*p, p, p);

        g2.setColor(fur); g2.fillRect(x + 8*p, y - 9*p, 2*p, 5*p);

        g2.fillRect(x + 9*p, y - 8*p, 3*p, 2*p);
        g2.fillRect(x + 12*p, y - 9*p, 2*p, 4*p);
        g2.fillRect(x + 13*p, y - 12*p, 2*p, 4*p);
        g2.fillRect(x + 10*p, y - 14*p, 3*p, 2*p);
        g2.fillRect(x + 9*p, y - 13*p, 2*p, 2*p);
    }

    // 【关键修复】新增绘制黑衣小偷（背猪者）瘫坐装死的特效方法
    private void drawDeadBlackThiefBody(Graphics2D g2, int dx, int dy) {
        int p = 3;
        Color clothesColor = new Color(30, 30, 35); // 黑衣
        Color skinColor = new Color(240, 190, 150);

        // 瘫坐在树旁的躯干
        g2.setColor(clothesColor);
        g2.fillRect(dx - 5*p, dy - 7*p, 7*p, 7*p);

        // 伸出的腿
        g2.fillRect(dx + 2*p, dy - 2*p, 6*p, 2*p);

        // 歪着的头
        g2.setColor(skinColor);
        g2.fillRect(dx - 2*p, dy - 11*p, 5*p, 5*p);

        // 黑色头发/帽子边沿
        g2.setColor(clothesColor);
        g2.fillRect(dx - 2*p, dy - 12*p, 5*p, 2*p);

        // 晕眩的眼睛 (X X)
        g2.setColor(Color.BLACK);
        g2.drawLine(dx - 1*p, dy - 10*p, dx, dy - 9*p);
        g2.drawLine(dx, dy - 10*p, dx - 1*p, dy - 9*p);
        g2.drawLine(dx + 1*p, dy - 10*p, dx + 2*p, dy - 9*p);
        g2.drawLine(dx + 2*p, dy - 10*p, dx + 1*p, dy - 9*p);

        // 头顶冒金星特效
        long t = System.currentTimeMillis() / 200;
        g2.setColor(new Color(255, 210, 40));
        if (t % 2 == 0) {
            g2.fillRect(dx - 4*p, dy - 15*p, p, p);
            g2.fillRect(dx + 3*p, dy - 13*p, p, p);
        } else {
            g2.fillRect(dx - 1*p, dy - 16*p, p, p);
            g2.fillRect(dx - 5*p, dy - 12*p, p, p);
        }
    }

    // 绿斗篷盗贼 (470分撞树者) 的眩晕特效
    private void drawDeadThiefBody(Graphics2D g2, int dx, int dy) {
        int p = 3;
        Color cloakColor = new Color(40, 60, 50);
        Color skinColor = new Color(240, 190, 150);
        Color maskColor = new Color(20, 20, 20);

        g2.setColor(cloakColor);
        g2.fillRect(dx - 5*p, dy - 7*p, 7*p, 7*p);

        g2.setColor(new Color(50, 40, 30));
        g2.fillRect(dx + 2*p, dy - 2*p, 6*p, 2*p);

        g2.setColor(skinColor);
        g2.fillRect(dx - 2*p, dy - 11*p, 5*p, 5*p);

        g2.setColor(maskColor);
        g2.fillRect(dx - 2*p, dy - 10*p, 5*p, 2*p);

        g2.setColor(Color.WHITE);
        g2.drawLine(dx - 1*p, dy - 10*p, dx, dy - 9*p);
        g2.drawLine(dx, dy - 10*p, dx - 1*p, dy - 9*p);
        g2.drawLine(dx + 1*p, dy - 10*p, dx + 2*p, dy - 9*p);
        g2.drawLine(dx + 2*p, dy - 10*p, dx + 1*p, dy - 9*p);

        long t = System.currentTimeMillis() / 200;
        g2.setColor(new Color(255, 210, 40));
        if (t % 2 == 0) {
            g2.fillRect(dx - 4*p, dy - 15*p, p, p);
            g2.fillRect(dx + 3*p, dy - 13*p, p, p);
        } else {
            g2.fillRect(dx - 1*p, dy - 16*p, p, p);
            g2.fillRect(dx - 5*p, dy - 12*p, p, p);
        }
    }

    private void drawDino(Graphics2D g2, int x, int y, boolean hideShield) {
        int p = 3;
        Color armor = new Color(210, 210, 220);
        Color plume = new Color(220, 40, 40);

        boolean isBlocking = (!isVictory && (isBlockingRight || isBlockingLeft));
        int bodyYOffset = isBlocking ? 3 : 0;

        g2.setColor(plume);
        g2.fillRect(x + 2 * p, y - 6 * p + bodyYOffset, 5 * p, 2 * p);
        g2.setColor(armor);
        g2.fillRect(x + 4 * p, y - 4 * p + bodyYOffset, 6 * p, 6 * p);
        g2.fillRect(x + 3 * p, y + 2 * p + bodyYOffset, 7 * p, 8 * p);

        if (!hideShield) {
            if (!isVictory) {
                if (isBlockingRight) { drawVividShield(g2, x + 9 * p, y - 1 * p); }
                else if (isBlockingLeft) { drawVividShield(g2, x - 5 * p, y - 1 * p); }
                else { drawBackShield(g2, x + 7 * p, y + 3 * p); }
            } else {
                drawBackShield(g2, x + 7 * p, y + 3 * p);
            }
        }

        g2.setColor(new Color(80, 85, 100));
        long anim = (System.currentTimeMillis() / 120) % 2;
        if (anim == 0 || isVictory || isBlocking) {
            g2.fillRect(x + 4 * p, y + 10 * p, 2 * p, 3 * p);
            g2.fillRect(x + 7 * p, y + 10 * p, 2 * p, 1 * p);
        } else {
            g2.fillRect(x + 4 * p, y + 10 * p, 2 * p, 1 * p);
            g2.fillRect(x + 7 * p, y + 10 * p, 2 * p, 3 * p);
        }
    }

    private void drawVividShield(Graphics2D g2, int sx, int sy) {
        int p = 3;
        long anim = System.currentTimeMillis() % 400;
        if (anim > 200) sy += 1;

        int width = 6 * p; int height = 12 * p;

        int[] xs = {sx, sx + width, sx + width, sx + width/2, sx};
        int[] ys = {sy, sy, sy + height/2 + p, sy + height, sy + height/2 + p};
        g2.setColor(new Color(170, 175, 185));
        g2.fillPolygon(xs, ys, 5);

        int innerP = 2;
        int[] inXs = {sx + innerP, sx + width - innerP, sx + width - innerP, sx + width/2, sx + innerP};
        int[] inYs = {sy + innerP, sy + innerP, sy + height/2 + p, sy + height - 2*innerP, sy + height/2 + p};
        g2.setColor(new Color(30, 60, 140));
        g2.fillPolygon(inXs, inYs, 5);

        g2.setColor(new Color(255, 210, 40));
        g2.fillRect(sx + width/2 - 1, sy + 3 * p, 2, height - 6 * p);
        g2.fillRect(sx + p + 1, sy + 5 * p, width - 2 * p - 2, 2);

        int alpha = 40 + (int)(Math.sin(System.currentTimeMillis() / 150.0) * 20);
        g2.setColor(new Color(255, 255, 255, Math.max(0, alpha)));
        g2.fillPolygon(new int[]{sx, sx + width/2, sx + width/2, sx},
                new int[]{sy, sy, sy + height/2, sy + height/2 + p}, 4);
    }

    private void drawBackShield(Graphics2D g2, int sx, int sy) {
        int p = 3;
        g2.setColor(new Color(170, 175, 185)); g2.fillRect(sx, sy, 3 * p, 7 * p);
        g2.setColor(new Color(30, 60, 140)); g2.fillRect(sx + 1, sy + 1, 3 * p - 2, 7 * p - 2);
    }

    private void drawOrcWithPig(Graphics2D g2, int x, int y) {
        Graphics2D g = (Graphics2D) g2.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
        int p = 5; long t = System.currentTimeMillis() / 200; int walkFrame = (int) (t % 2);
        int bounce = (walkFrame == 0) ? p : 0;
        int offsetY = -3 * p - 6;

        Color thiefSkin = new Color(240, 190, 150); Color thiefBlack = new Color(30, 30, 35);
        Color pigPink = new Color(255, 170, 190); Color pigDark = new Color(220, 130, 150);
        Color crownGold = new Color(255, 200, 0); Color ropeColor = new Color(101, 67, 33);

        g.setColor(thiefBlack);
        if (walkFrame == 0) { drawRect(g, x + 3 * p, y + 10 * p + offsetY, 2 * p, 3 * p, p); drawRect(g, x + 7 * p, y + 11 * p + offsetY, 3 * p, 2 * p, p); }
        else { drawRect(g, x + 3 * p, y + 11 * p + offsetY, 3 * p, 2 * p, p); drawRect(g, x + 8 * p, y + 10 * p + offsetY, 2 * p, 3 * p, p); }
        int px = x - 5 * p; int py = y - 7 * p + bounce + offsetY;
        g.setColor(pigDark); drawRect(g, px, py + 2 * p, 16 * p, 10 * p, p);
        g.setColor(pigPink); drawRect(g, px + p, py + p, 14 * p, 10 * p, p);
        g.setColor(new Color(255, 110, 140)); drawRect(g, px - 2 * p, py + 5 * p, 4 * p, 4 * p, p);
        g.setColor(new Color(60, 20, 30)); drawRect(g, px - p, py + 6 * p, p, 2 * p, p);
        g.setColor(Color.WHITE); drawRect(g, px + 3 * p, py + 3 * p, 2 * p, 2 * p, p); drawRect(g, px + 8 * p, py + 3 * p, 2 * p, 2 * p, p);
        g.setColor(Color.BLACK); drawRect(g, px + 4 * p, py + 3 * p, p, p, p);
        g.setColor(crownGold); drawRect(g, px + 6 * p, py - 2 * p, 5 * p, p, p); drawRect(g, px + 6 * p, py - 3 * p, p, p, p);
        drawRect(g, px + 8 * p, py - 4 * p, p, 2 * p, p); drawRect(g, px + 10 * p, py - 3 * p, p, p, p);
        g.setColor(thiefBlack); drawRect(g, x + 2 * p, y + 5 * p + offsetY, 10 * p, 5 * p, p);
        int hx = x + 10 * p; int hy = y + 5 * p + offsetY; drawRect(g, hx, hy, 5 * p, 4 * p, p);
        g.setColor(thiefSkin); drawRect(g, hx + 2 * p, hy + p, 3 * p, 2 * p, p);
        g.setColor(Color.BLACK); int eyeShift = (int) (System.currentTimeMillis() / 400 % 2);
        drawRect(g, hx + 2 * p + eyeShift, hy + p, 1, 1, p); drawRect(g, hx + 4 * p, hy + p, 1, 1, p);
        g.setColor(ropeColor); drawRect(g, px + 5 * p, py, p, 12 * p, p); drawRect(g, px + 11 * p, py, p, 11 * p, p);
        g.setColor(thiefSkin); drawRect(g, x + p, y + 7 * p + bounce + offsetY, 2 * p, 2 * p, p);
        g.dispose();
    }

    private void drawRect(Graphics2D g, int x, int y, int w, int h, int p) { g.fillRect(x, y, w, h); }

    private void drawThief(Graphics2D g2, int x, int y) {
        int p = 3;
        long t = System.currentTimeMillis() / 150;
        AffineTransform oldTx = g2.getTransform();
        int cx = x + 4 * p; int cy = y + 5 * p;
        g2.translate(cx, cy);

        boolean inAir = (thiefY < GROUND_Y - 40);
        if (inAir) { g2.rotate((System.currentTimeMillis() % 600) / 600.0 * 2 * Math.PI); }
        else { if (t % 2 == 0) g2.translate(0, 2); }

        int rx = -4 * p; int ry = -5 * p;
        Color cloakColor = new Color(40, 60, 50); Color skinColor = new Color(240, 190, 150);
        Color clothesColor = new Color(100, 80, 60); Color maskColor = new Color(20, 20, 20);

        g2.setColor(clothesColor); g2.fillRect(rx + p, ry + 2 * p, 5 * p, 7 * p);
        g2.setColor(cloakColor); g2.fillRect(rx - 2 * p, ry + 2 * p, 3 * p, 6 * p); g2.fillRect(rx + p, ry - 3 * p, 6 * p, 4 * p);
        g2.setColor(skinColor); g2.fillRect(rx + 2 * p, ry - p, 4 * p, 3 * p);
        g2.setColor(maskColor); g2.fillRect(rx + 2 * p, ry, 4 * p, p);
        g2.setColor(Color.WHITE); g2.fillRect(rx + 4 * p, ry, p, p);

        g2.setColor(new Color(50, 40, 30));
        if (inAir) { g2.fillRect(rx + p, ry + 9 * p, 2 * p, 2 * p); g2.fillRect(rx + 5 * p, ry + 7 * p, 2 * p, 2 * p); }
        else { if (t % 2 == 0) g2.fillRect(rx + 2 * p, ry + 9 * p, 2 * p, 2 * p); else g2.fillRect(rx + 5 * p, ry + 9 * p, 2 * p, 2 * p); }

        if (thiefThrowTimer > 0) {
            g2.setColor(clothesColor); g2.fillRect(rx + 6 * p, ry + 3 * p, 4 * p, 2 * p);
            g2.setColor(skinColor); g2.fillRect(rx + 10 * p, ry + 3 * p, p, 2 * p);
        } else {
            g2.setColor(clothesColor);
            if (inAir) { g2.fillRect(rx + 2 * p, ry + 4 * p, 2 * p, 3 * p); }
            else { if (t % 2 == 0) g2.fillRect(rx + 3 * p, ry + 4 * p, 2 * p, 3 * p); else g2.fillRect(rx + 5 * p, ry + 4 * p, 2 * p, 3 * p); }
        }
        g2.setTransform(oldTx);
    }

    private void drawFancyClouds(Graphics2D g2) {
        for (int i = 0; i < clouds.size(); i++) {
            Point p = clouds.get(i);
            g2.setColor(new Color(230, 230, 230, 180));
            g2.fillOval(p.x + 10, p.y + 10, 40, 20); g2.fillOval(p.x + 25, p.y, 35, 25); g2.fillOval(p.x + 45, p.y + 5, 30, 20);
            g2.setColor(new Color(255, 255, 255, 220));
            g2.fillOval(p.x + 8, p.y + 8, 40, 20); g2.fillOval(p.x + 23, p.y - 2, 35, 25); g2.fillOval(p.x + 43, p.y + 3, 30, 20);
        }
    }

    class Bomb {
        double x, y, vx, vy; boolean onGround = false;
        Bomb(int x, int y) { this.x = x; this.y = y; this.vx = -4.5 - rand.nextDouble() * 2; this.vy = -7; }
        boolean update(double speed) {
            if (!onGround) { x += vx; y += vy; vy += 0.5; if (y >= GROUND_Y - 15) { y = GROUND_Y - 15; onGround = true; } }
            else x -= speed; return x < -50;
        }
        void render(Graphics2D g2) {
            g2.setColor(Color.BLACK); g2.fillOval((int) x, (int) y, 15, 15);
            g2.setColor(new Color(150, 150, 150)); g2.fillOval((int) x + 3, (int) y + 3, 5, 5);
            g2.setColor(new Color(180, 50, 50)); g2.fillRect((int) x + 6, (int) y - 4, 3, 5);
            g2.setColor(new Color(255, 200 + rand.nextInt(55), 0));
            for (int i = 0; i < 4; i++) g2.fillRect((int) x + 7 + rand.nextInt(10) - 5, (int) y - 6 + rand.nextInt(8) - 4, 2, 2);
            g2.setColor(Color.WHITE); g2.fillRect((int) x + 7 + rand.nextInt(4) - 2, (int) y - 5 + rand.nextInt(4) - 2, 2, 2);
        }
        Rectangle getBounds() { return new Rectangle((int) x + 3, (int) y + 3, 9, 9); }
    }

    class Dagger {
        double x, y; Dagger(int x, int y) { this.x = x; this.y = y; }
        boolean update(double speed) { x += (speed + 4.5); return x > 1000; }
        void render(Graphics2D g2) {
            AffineTransform oldTx = g2.getTransform();
            g2.translate(x + 10, y + 5); g2.rotate(System.currentTimeMillis() / 80.0);
            g2.setColor(new Color(200, 200, 205)); g2.fillPolygon(new int[]{0, 12, 0}, new int[]{-3, 0, 3}, 3);
            g2.setColor(new Color(101, 67, 33)); g2.fillRect(-8, -2, 8, 4);
            g2.setColor(new Color(80, 80, 80)); g2.fillRect(-2, -4, 3, 8);
            g2.setTransform(oldTx);
        }
        Rectangle getBounds() { return new Rectangle((int) x, (int) y, 16, 10); }
    }

    class Obstacle {
        double x; int y;
        boolean isKnockedDown = false;
        boolean hasCrashedThief = false;

        Obstacle(int x, int y) { this.x = x; this.y = y; }
        void render(Graphics2D g2) {
            AffineTransform oldTx = g2.getTransform();
            if (isKnockedDown) {
                g2.translate(x + 10, y + 65);
                g2.rotate(Math.PI / 2);
                g2.translate(-(x + 10), -(y + 65));
            }
            g2.setColor(new Color(90, 55, 30)); g2.fillRect((int) x + 10, y + 30, 14, 35);
            g2.setColor(new Color(60, 35, 15));
            g2.drawLine((int) x + 13, y + 30, (int) x + 13, y + 65);
            g2.drawLine((int) x + 17, y + 35, (int) x + 17, y + 65);
            g2.drawLine((int) x + 21, y + 30, (int) x + 21, y + 65);

            g2.setColor(new Color(30, 110, 50));
            g2.fillPolygon(new int[]{(int) x + 17, (int) x - 18, (int) x + 52}, new int[]{y + 25, y + 55, y + 55}, 3);
            g2.fillPolygon(new int[]{(int) x + 17, (int) x - 13, (int) x + 47}, new int[]{y + 10, y + 40, y + 40}, 3);
            g2.fillPolygon(new int[]{(int) x + 17, (int) x - 8, (int) x + 42}, new int[]{y - 5, y + 20, y + 20}, 3);
            g2.setColor(new Color(50, 140, 70));
            g2.fillPolygon(new int[]{(int) x + 17, (int) x + 5, (int) x + 29}, new int[]{y - 5, y + 15, y + 15}, 3);
            g2.setTransform(oldTx);

            if (hasCrashedThief) drawDeadThiefBody(g2, (int)x, y + 60);
        }
        Rectangle getBounds() { return new Rectangle((int) x + 10, y + 25, 16, 35); }
    }

    class DustParticle {
        double x, y, vx, vy; int life = 255;
        DustParticle(int x, int y) { this.x = x; this.y = y; this.vx = -3 - rand.nextDouble() * 2; this.vy = -rand.nextDouble(); }
        boolean update() { x += vx; y += vy; life -= 12; return life <= 0; }
        void draw(Graphics2D g2) { g2.setColor(new Color(200, 200, 200, life)); g2.fillOval((int) x, (int) y, 5, 5); }
    }
}