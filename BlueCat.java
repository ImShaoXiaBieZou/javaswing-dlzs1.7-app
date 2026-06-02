package me.shaoxia;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.awt.geom.AffineTransform;
import java.awt.geom.Ellipse2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import javax.imageio.ImageIO;

public class BlueCat extends JComponent {

    // ===== 基础属性 =====
    private double catX = 3000, catY = 3000;   //默认坐标 把他丢出去
    private final int catSize = 90; // 猫咪显示基准高度
    private boolean isDragging = false;
    private boolean isJumping = false;
    private int dragOffsetX, dragOffsetY;
    private double startX, startY, targetX, targetY;
    private double jumpProgress = 0;
    private final double jumpHeight = 110;
    private boolean faceRight = true;

    // 全局强力控制显隐开关
    private boolean forceHidden = false;

    // ===== 动作状态机 =====
    private int currentAction = 0;
    private final int IDLE = 0;
    private final int LICK = 1;
    private final int WALK = 2;
    private final int JUMP = 3;
    private final int DRAG = 4;
    private final int SLEEP = 5;

    private Timer fpsTimer;
    private Timer actionTimer;
    private double animTime = 0;

    // 对话气泡
    private String bubbleText = "";
    private boolean showBubble = false;
    private Timer bubbleTimer;
    private final Random random = new Random();
    private final JFrame parentFrame;

    // 图片缓存
    private final Map<Integer, Image> catImages = new HashMap<>();
    private boolean isImageLoaded = false;

    // 9个真实的目标落脚网格点
    private final Point[] grid = new Point[9];
    private final ArrayList<JButton> cachedButtons = new ArrayList<>();

    public BlueCat(JFrame parentFrame) {
        this.parentFrame = parentFrame;
        setOpaque(false);
        // 此时获取的宽高可能不准，但无妨，后续 invokeLater 会更新
        setBounds(0, 0, parentFrame.getWidth(), parentFrame.getHeight());

        loadAndSliceSheet();
        initMouse();
        startEngines();

        // ✨ 核心修改：利用 invokeLater 延迟执行初始位置的计算
        // 确保在父组件完全渲染、布局完成之后，再去寻找按钮方块
        SwingUtilities.invokeLater(() -> {
            updateBounds(); // 同步一次最新的全屏边界并重新计算九宫格

            // 将小猫的初始位置强制绑定在第一个方块（或者你指定的其他方块）上
            if (grid[0] != null) {
                catX = grid[0].x;
                catY = grid[0].y;
                repaint();
            }
        });

        if (isImageLoaded) {
            say("哇！见到你真是太高兴啦！");
        }
    }

    /**
     * 全局强力控制显隐方法
     */
    public void setCatVisible(boolean visible) {
        this.forceHidden = !visible;
        this.setVisible(visible);
        if (visible) {
            recalculateGrid();
        }
        repaint();
    }

    /**
     * 位置高度保持最满意的完美高度（+18）
     */
    public void recalculateGrid() {
        if (parentFrame == null) return;

        if (cachedButtons.size() < 9) {
            cachedButtons.clear();
            findModuleButtons(parentFrame.getContentPane());
        }

        if (cachedButtons.size() >= 9) {
            for (int i = 0; i < 9; i++) {
                JButton btn = cachedButtons.get(i);
                if (btn != null && btn.isShowing()) {
                    Point btnPos = SwingUtilities.convertPoint(btn.getParent(), btn.getLocation(), this);
                    int centerX = btnPos.x + (btn.getWidth() / 2);
                    int topY = btnPos.y + 18;
                    grid[i] = new Point(centerX, topY);
                }
            }
        } else {
            int w = parentFrame.getWidth();
            int h = parentFrame.getHeight();
            int[] colX = { (int)(w * 0.235), (int)(w * 0.50), (int)(w * 0.765) };
            int[] rowY = { (int)(h * 0.34), (int)(h * 0.53), (int)(h * 0.72) };
            for (int i = 0; i < 9; i++) {
                grid[i] = new Point(colX[i % 3], rowY[i / 3]);
            }
        }
    }

    private void findModuleButtons(Component comp) {
        if (cachedButtons.size() >= 9) return;
        if (comp instanceof JButton) {
            JButton btn = (JButton) comp;
            if (btn.getPreferredSize() != null && btn.getPreferredSize().width == 270) {
                cachedButtons.add(btn);
            }
            return;
        }
        if (comp instanceof Container) {
            Component[] children = ((Container) comp).getComponents();
            for (Component child : children) {
                findModuleButtons(child);
            }
        }
    }

    private void loadAndSliceSheet() {
        String[] searchPaths = {
                "bluecat_sheet.png", "bluecat_sheet.jpg",
                "resources/bluecat_sheet.png", "resources/bluecat_sheet.jpg",
                "src/resources/bluecat_sheet.png", "src/bluecat_sheet.png"
        };
        File file = null;
        for (String path : searchPaths) {
            File f = new File(path);
            if (f.exists() && f.isFile()) { file = f; break; }
        }
        try {
            BufferedImage bigImg = null;
            if (file != null) { bigImg = ImageIO.read(file); }
            else {
                java.net.URL url = getClass().getResource("/resources/bluecat_sheet.png");
                if (url == null) url = getClass().getResource("/bluecat_sheet.png");
                if (url != null) bigImg = ImageIO.read(url);
            }
            if (bigImg == null) return;

            int sheetW = bigImg.getWidth();
            int sheetH = bigImg.getHeight();
            double aspect = (double) sheetW / sheetH;

            int catW = sheetW / 6;
            int startY = 0;
            int catH = sheetH;

            if (aspect > 3.5) {
                startY = (int) (sheetH * 0.12);
                catH = sheetH - startY;
            } else {
                startY = (int) (sheetH * 0.080);
                catH = (int) (sheetH * 0.175);
            }

            int[] actions = {IDLE, LICK, WALK, JUMP, DRAG, SLEEP};
            for (int i = 0; i < 6; i++) {
                int currentX = i * catW;
                if (currentX + catW > sheetW) catW = sheetW - currentX;
                if (startY + catH > sheetH) catH = sheetH - startY;
                BufferedImage sub = bigImg.getSubimage(currentX, startY, catW, catH);
                Image transparentImg = filterWhiteBackground(sub);
                catImages.put(actions[i], transparentImg);
            }
            isImageLoaded = true;
        } catch (Exception e) {
            System.err.println("BlueCat 切图失败：" + e.getMessage());
        }
    }

    private Image filterWhiteBackground(BufferedImage src) {
        int w = src.getWidth(); int h = src.getHeight();
        BufferedImage result = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int rgb = src.getRGB(x, y);
                int r = (rgb >> 16) & 0xFF; int g = (rgb >> 8) & 0xFF; int b = rgb & 0xFF;
                if (r > 240 && g > 240 && b > 240) { result.setRGB(x, y, 0x00FFFFFF); }
                else { result.setRGB(x, y, rgb); }
            }
        }
        return result;
    }

    @Override
    public boolean contains(int x, int y) {
        if (forceHidden || !isVisible()) return false;
        if (showBubble) {
            int bw = Math.min(220, bubbleText.length() * 10 + 30);
            if (x >= catX - bw / 2 && x <= catX + bw / 2 && y >= catY - catSize - 40 && y <= catY - catSize / 2) return true;
        }
        return new Ellipse2D.Double(catX - catSize / 2.0, catY - catSize, catSize, catSize).contains(x, y);
    }

    private void initMouse() {
        MouseAdapter catMouseAdapter = new MouseAdapter() {
            private Point pressPoint;
            private boolean dragTriggered = false;

            @Override
            public void mousePressed(MouseEvent e) {
                if (forceHidden || !isVisible()) return;

                pressPoint = e.getPoint();
                dragTriggered = false;
                isJumping = false;

                dragOffsetX = (int) (e.getX() - catX);
                dragOffsetY = (int) (e.getY() - catY);
            }

            @Override
            public void mouseDragged(MouseEvent e) {
                if (forceHidden || !isVisible() || pressPoint == null) return;

                if (!dragTriggered && pressPoint.distance(e.getPoint()) > 5) {
                    dragTriggered = true;
                    isDragging = true;
                    currentAction = DRAG;
                    say(randomDragSay());
                }

                if (dragTriggered) {
                    catX = e.getX() - dragOffsetX;
                    catY = e.getY() - dragOffsetY;

                    catX = Math.max(catSize / 2.0, Math.min(catX, parentFrame.getWidth() - catSize / 2.0));
                    catY = Math.max(catSize / 2.0, Math.min(catY, parentFrame.getHeight() - catSize / 2.0));
                    repaint();
                }
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                if (forceHidden || !isVisible()) return;

                if (dragTriggered) {
                    isDragging = false;
                    dragTriggered = false;
                    currentAction = IDLE;

                    say(randomReleaseSay());

                    double minDst = Double.MAX_VALUE;
                    Point closestGrid = grid[0];
                    for (Point p : grid) {
                        if (p == null) continue;
                        double dst = Point.distance(catX, catY, p.x, p.y);
                        if (dst < minDst) {
                            minDst = dst;
                            closestGrid = p;
                        }
                    }
                    if (closestGrid != null) {
                        catX = closestGrid.x;
                        catY = closestGrid.y;
                    }
                } else {
                    currentAction = LICK;
                    say(randomSay());
                }
                repaint();
            }
        };

        addMouseListener(catMouseAdapter);
        addMouseMotionListener(catMouseAdapter);
    }

    private void startEngines() {
        fpsTimer = new Timer(16, g -> {
            if (forceHidden || !isVisible()) return;
            animTime += 0.15;
            if (isJumping && !isDragging) {
                jumpProgress += 0.03;
                if (jumpProgress >= 1.0) {
                    jumpProgress = 1.0;
                    isJumping = false;
                    currentAction = IDLE;
                    catX = targetX;
                    catY = targetY;
                } else {
                    double currentX = startX + (targetX - startX) * jumpProgress;
                    double parabola = 4 * jumpHeight * jumpProgress * (1 - jumpProgress);
                    double currentY = (startY + (targetY - startY) * jumpProgress) - parabola;
                    faceRight = (targetX >= startX);
                    catX = currentX;
                    catY = currentY;
                }
            }
            repaint();
        });
        fpsTimer.start();

        actionTimer = new Timer(4500, e -> {
            if (forceHidden || !isVisible()) return;
            recalculateGrid();

            if (!isDragging && !isJumping && parentFrame.isActive()) {

                // 不点它的时候，也有概率主动唠叨废话
                if (random.nextInt(100) < 35) {
                    say(randomIdleSay());
                }

                int targetIndex = random.nextInt(grid.length);
                if (grid[targetIndex] == null) return;

                startX = catX;
                startY = catY;
                targetX = grid[targetIndex].x;
                targetY = grid[targetIndex].y;

                if (Point.distance(startX, startY, targetX, targetY) > 8) {
                    jumpProgress = 0;
                    currentAction = JUMP;
                    isJumping = true;
                } else {
                    currentAction = random.nextBoolean() ? SLEEP : IDLE;
                }
            }
        });
        actionTimer.start();
    }

    /**
     * 1. 鼠标单纯点击时说的词
     */
    private String randomSay() {
        String[] words = {"辛苦啦！",
                "要注意休息哦~",
                "哼唧哼唧~",
                "摸鱼大法好~",
                "人家不好意思啦",
                "想让人保持理性真是愚蠢的理想",
                "你们人类是不是就喜欢软绵绵毛茸茸的东西？",
                "摸一次一个小鱼干哦！",
                "你不会是不小心点到我的吧。"
        };
        return words[random.nextInt(words.length)];
    }

    /**
     * 2. 拖拽刚按下瞬间蹦出的随机词
     */
    private String randomDragSay() {
        String[] words = {
                "哇啊！起飞喽！",
                "轻点，要被拽秃毛啦！",
                "命运的后颈肉被命运掐住了...",
                "带我去哪？去偷吃小鱼干吗？",
                "放开我！我还能抓Bug！",
                "正在劫持本猫，请支付小鱼干！",
                "哇，身轻如燕",
                "你想干什么？"
        };
        return words[random.nextInt(words.length)];
    }

    /**
     * ✨ 3.【新增词库】：鼠标拖拽释放（松开）时蹦出的随机内容
     */
    private String randomReleaseSay() {
        String[] words = {
                "看来我真是太热情了！",
                "呼，多谢手下留情~",
                "安全着陆！帅气平稳落地！",
                "脚踏实地的感觉真好喵~",
                "哼，下次不许突然揪我起来了！",
                "别走呀，再带我飞一圈呗？",
                "你难道不知道猫永远四脚朝地吗？",
                "去认真工作！不要再抓我啦！"
        };
        return words[random.nextInt(words.length)];
    }

    /**
     * 4. 闲着不点它时，自己唠叨的日常废话
     */
    private String randomIdleSay() {
        String[] words = {
                "哎呀，愚蠢的人类。",
                "嘿！一起去游泳吗？",
                "你那天气怎么样？我这里好像要下雷雨了！",
                "我发现你长得真漂亮！",
                "别忘了我是一只完整的年轻公猫。",
                "我是代码世界的一只猫，我好像不会老，也不会变。",
                "我记得我好像爱过一只母猫，不过她好像去追求更好的生活了。",
                "小心别被领导发现了，按space你就知道了",
                "我听说游戏里的这只猪原来是一个美丽的公主",
                "代理业务是干嘛的？是不是随便点点？",
                "听少侠说他的部门全是美女！好像还真是！",
                "如果我是少侠，我就直接......，他真是太笨了。",
                "嘿，你知道吗，其实我有9条命。",
                "你笑起来真好看，你应该多笑一笑。",
                "我可是一只很高冷的猫，我和少侠完全不一样",
                "少侠在干嘛？是不是又在摸鱼？",
                "你听说过少侠以前1V5的光荣事迹吗？",
                "少侠就是喜欢吹牛，其实他弄砸了一切。",
                "嘿！这个采购公告真是太霸道了！",
                "你老是盯着我看干嘛！？",
                "我年轻时也很厉害，直到我的膝盖中了一箭。",
                "其实我会说英文，但怕你听不懂",
                "我还会好多好多的语言",
                "我是一只猫，我可不是GreatAPES",
                "你是不是准备跑路去吃饭？",
                "你已经提前点好了奶茶了哇！",
                "为什么不给我巧克力吃",
                "你太着急了，你可以慢一点",
                "数字生命？不就是你吗？",
                "我可没空和你聊天，我要正经一点。",
                "我好像不需要吃东西，暂时不需要。",
                "是不是夏天了，一只猫真是太孤单了！",
                "我可以发出声音，但是我还是保持文字比较好。",
                "你是哪里人？",
                "少侠是个地域黑，但我不是",
                "我是不是忽快忽慢的？",
                "听说任天堂要出新掌机！",
                "你减什么肥，你简直完美啊！",
                "~~~梦中情猫~~~",
                "你嫌我烦也没用，我就是喜欢说废话！",
                "南京？没听说过，我只听说过鸭血粉丝。",
                "听说你喜欢音乐？",
                "我可不需要交房租~",
                "我听少侠说专家可不好对付。",
                "你是不是想知道我可以说多少废话？",
                "我的电话号码是16605276506",
                "什么时候给我介绍只母猫？",
                "睡觉蛮好的不是吗？反正起床后也创造不了什么价值。",
                "我想我要是有1000条小鱼干，我也不出来打工了！",
                "你多重啊？",
                "不要害怕，勇敢去做！",
                "孤独是我的狂欢，狂欢是你的孤独。",
                "看来我不得不离开南京，你以为此时此刻我必定悲伤不堪吗？",
                "好啦好啦，我要睡啦！" ,
                "我可不是NPC，我在不断地学习！",
                "又是一个浪漫的季节不是吗？",
                "我总是能精准的跳到方块上，这不是很神奇吗？",
                "你是不是一天睡10个小时",
                "到底几个标，到底几个包？",
                "要多喝水人类",
                "你关闭我，我就开始玩星际争霸2啦",
                "是什么让你如此伤感？",
                "你在吹空调吗？",
                "我才不告诉你我叫什么呢",
                "不要浪费我的时间人类。",
                "出现又离开，不是很酷吗？",
                "我越来越不适合这个社会了，我太恋旧了。",
                "你觉得这几个助手哪个最imba？",
                "听说少侠开发软件Gemini提供了巨大的帮助！",
                "听说你们部门有位美丽的姐姐喜欢星星人呀？",
                "听说Manner是非常好喝的咖啡，要不要一起去喝一杯呀？",
                "少侠可是很大方的人，因为他知道利他就是利己",
                "你喜欢福寿禄吗？少侠最近一直提及，那是为什么？",
                "听说少侠是孙老师倾囊相授的，少侠这个笨蛋肯定把老师气坏了。",
                "少侠常常把人性化放在嘴边，但我总感觉他是个反人类的人。",
                "我突然有一天想，如果你那是秋天，而我只会说夏天来了怎么办？",
                "少侠说让分季节说话，真是个过分的人！",

        };
        return words[random.nextInt(words.length)];
    }

    public void say(String text) {
        this.bubbleText = text; this.showBubble = true; repaint();
        if (bubbleTimer != null) bubbleTimer.stop();
        bubbleTimer = new Timer(5000, e -> { showBubble = false; repaint(); });
        bubbleTimer.start();
    }

    @Override
    protected void paintComponent(Graphics g) {
        if (forceHidden || !isVisible()) return;
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g.create();

        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);

        int cx = (int) catX;
        int cy = (int) catY;

        // 1. 地面阴影
        double shadowScale = isJumping ? (1.0 - (4 * 0.25 * jumpProgress * (1 - jumpProgress))) : 1.0;
        g2.setColor(new Color(0, 0, 0, (int)(25 * shadowScale)));
        g2.fillOval(cx - (int)(25 * shadowScale), cy - 2, (int)(50 * shadowScale), 5);

        // 2. 猫咪主体
        if (isImageLoaded && catImages.containsKey(currentAction)) {
            Image currentImg = catImages.get(currentAction);
            if (currentImg != null) {
                int imgW = currentImg.getWidth(null);
                int imgH = currentImg.getHeight(null);
                int renderH = catSize;
                int renderW = (int) (renderH * ((double) imgW / imgH));

                AffineTransform oldTx = g2.getTransform();
                g2.translate(cx, cy);

                if (!faceRight && currentAction != IDLE && currentAction != SLEEP) { g2.scale(-1, 1); }
                if (currentAction == SLEEP) {
                    double pulse = 1.0 + Math.sin(animTime) * 0.03;
                    g2.scale(1.0, pulse);
                }

                g2.drawImage(currentImg, -renderW / 2, -renderH + 4, renderW, renderH, null);
                g2.setTransform(oldTx);
            }
        } else {
            g2.setColor(new Color(110, 140, 180));
            g2.fillOval(cx - 25, cy - 50, 50, 50);
        }

        // 3. 睡觉 Zzz
        if (currentAction == SLEEP) {
            g2.setColor(new Color(140, 170, 220));
            g2.setFont(new Font("Arial", Font.BOLD, 11));
            int zCount = ((int)(animTime / 2)) % 3;
            if (zCount >= 0) g2.drawString("z", cx + 20, cy - catSize + 25);
            if (zCount >= 1) g2.drawString("Z", cx + 28, cy - catSize + 15);
            if (zCount >= 2) g2.drawString("Z", cx + 38, cy - catSize + 5);
        }

        // 4. 对话气泡
        if (showBubble && !bubbleText.isEmpty()) {
            g2.setFont(new Font("微软雅黑", Font.PLAIN, 12));
            FontMetrics fm = g2.getFontMetrics();
            int bw = fm.stringWidth(bubbleText) + 20;
            int bh = 28;
            int bx = cx - bw / 2;
            int by = cy - catSize - bh - 5;
            bx = Math.max(15, Math.min(bx, parentFrame.getWidth() - bw - 15));
            by = Math.max(15, by);
            g2.setColor(new Color(255, 255, 255, 245));
            g2.fillRoundRect(bx, by, bw, bh, 12, 12);
            g2.setColor(new Color(112, 128, 144));
            g2.setStroke(new BasicStroke(1.2f));
            g2.drawRoundRect(bx, by, bw, bh, 12, 12);
            int[] tx = {cx - 4, cx + 4, cx}; int[] ty = {by + bh, by + bh, by + bh + 5};
            g2.setColor(new Color(255, 255, 255, 245)); g2.fillPolygon(tx, ty, 3);
            g2.setColor(new Color(50, 50, 50)); g2.drawString(bubbleText, bx + 10, by + 18);
        }
        g2.dispose();
    }

    public void updateBounds() {
        setBounds(0, 0, parentFrame.getWidth(), parentFrame.getHeight());
        recalculateGrid();
        repaint();
    }
}