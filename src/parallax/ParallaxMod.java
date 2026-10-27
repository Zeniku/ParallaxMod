package parallax;

import arc.*;
import arc.graphics.Color;
import arc.graphics.g2d.*;
import arc.math.*;
import arc.util.*;
import mindustry.content.Blocks;
import mindustry.game.EventType.*;
import mindustry.graphics.*;
import mindustry.mod.*;
import mindustry.world.*;
import mindustry.world.blocks.defense.Wall;
import mindustry.world.blocks.environment.StaticWall;

import static mindustry.Vars.*;

public class ParallaxMod extends Mod{
    static boolean enabled = true;
    static float strength = 0.82f;

    // base height of each group (multiplied by strength every frame)
    static final float
        baseWalls = 0.02f,   // static map walls + the darkness overlay (must match)
        baseUnder = 0f,      // cached "under" buildings (0 = flat), also Layer.blockUnder
        baseBlock = 0.02f;   // Layer.block: uncached blocks + cached "normal" buildings

    // current frame values (base * strength), shared with drawSides / kFor
    static float wallK, underK, blockK;

    // {layer, base k}
    static final float[][] layers = {
        {Layer.blockUnder,    baseUnder},
        {Layer.block,         baseBlock},   // also covers cracks, additive, props, blockOver, blockBuilding
        {Layer.turret,        0.04f},
        {Layer.groundUnit,    0.03f},
        {Layer.power,         baseBlock},   // lines meet node tops
        {Layer.legUnit,       0.04f},
        {Layer.darkness,      baseWalls},   // must match the wall height
        {Layer.plans,         baseBlock},
        {Layer.flyingUnitLow, 0.04f},
        {Layer.bullet,        0.04f},
        {Layer.effect,        0.04f},
        {Layer.flyingUnit,    0.04f},
        {Layer.overlayUI, 0.04f}
    };

    // order matches dirs: left, right, bottom, top
    static final int[][] dirs = {{-1,0},{1,0},{0,-1},{0,1}};
    static final float[] shade = {0.55f, 0.75f, 0.65f, 0.85f};
    static final Mat mat = new Mat();

    // camera matrices for the cached draws (walls + building cache)
    static final Mat base = new Mat(), tmp = new Mat();
    static final Color fallback = Color.valueOf("555555");

    @Override
    public void init(){
        Events.run(Trigger.draw, () -> {
            if(!enabled){
                Draw.trans(mat.idt());
                return;
            }

            float cx = Core.camera.position.x, cy = Core.camera.position.y;
            wallK = baseWalls * strength;
            underK = baseUnder * strength;
            blockK = baseBlock * strength;

            Draw.draw(Layer.floor - 1f, () -> Draw.trans(mat.idt()));

            drawSides(cx, cy);
            scaleGroups(cx, cy);

            for(float[] l : layers){
                float z = l[0], k = l[1] * strength;
                Draw.draw(z - 0.001f, () -> {
                    mat.setToTranslation(cx, cy);
                    mat.scale(1f + k, 1f + k);
                    mat.translate(-cx, -cy);
                    Draw.trans(mat);
                });
            }

            // dont let anything on top be affected
            Draw.draw(Layer.flyingUnit + 0.5f, () -> Draw.trans(mat.idt()));
        });

        Events.run(Trigger.uiDrawBegin, () -> Draw.trans(mat.idt()));
    }

    /*
     * Scales the cached draws (they bypass Draw.trans): 
     * static walls and the two building cache layers. 
     * so just Scale the camera itself
    */
    static void scaleGroups(float cx, float cy){
        base.set(Core.camera.mat); // unscaled at this moment

        // under cache (its callback at block - 0.5 binds camera.mat)
        Draw.draw(Layer.block - 0.501f, () -> setCam(underK, cx, cy));
        Draw.draw(Layer.block - 0.499f, () -> Core.camera.mat.set(base));

        // static walls (drawn at block - 0.09)
        Draw.draw(Layer.block - 0.1f, () -> setCam(wallK, cx, cy));
        Draw.draw(Layer.block - 0.08f, () -> Core.camera.mat.set(base));

        // normal cache: rebind the shader uniform before it draws at block
        Draw.draw(Layer.block - 0.0005f, () -> {
            tmp.set(base).translate(cx, cy).scale(1f + blockK, 1f + blockK).translate(-cx, -cy);
            SpriteCache.getDefaultShader().bind();
            SpriteCache.getDefaultShader().setUniformMatrix4("u_projectionViewMatrix", tmp);
        });
    }

    // set the camera
    static void setCam(float k, float cx, float cy){
        Core.camera.mat.set(base).translate(cx, cy).scale(1f + k, 1f + k).translate(-cx, -cy);
    }

    // determines which blocks are considered "tall"
    static boolean tall(Tile t){
        return t != null && t.block() != Blocks.air && t.block().solid
            || (t.block() instanceof Wall || t.block() instanceof StaticWall) ;
    }

    /** Height of a block's top face, matching whichever group it is drawn in. */
    static float kFor(Block b){
        if(b instanceof StaticWall) return wallK;
        if(b.drawCached) return b.buildingCacheLayer == BuildingCacheLayer.under ? underK : blockK;
        return blockK;
    }

    static Color baseColor(Block b){
        Color c = b.mapColor;
        return c == null || c.r + c.g + c.b < 0.03f ? fallback : c;
    }

    static void drawSides(float cx, float cy){
        float h = tilesize / 2f;

        Core.camera.bounds(Tmp.r1);
        int x0 = Math.max((int)(Tmp.r1.x / tilesize) - 2, 0);
        int y0 = Math.max((int)(Tmp.r1.y / tilesize) - 2, 0);
        int x1 = Math.min((int)((Tmp.r1.x + Tmp.r1.width) / tilesize) + 2, world.width() - 1);
        int y1 = Math.min((int)((Tmp.r1.y + Tmp.r1.height) / tilesize) + 2, world.height() - 1);

        // draw before blocks so it "culls"
        Draw.z(Layer.blockUnder + 0.01f);

        for(int ty = y0; ty <= y1; ty++){
            for(int tx = x0; tx <= x1; tx++){
                Tile t = world.tile(tx, ty);
                if(!tall(t)) continue;

                float s = 1f + kFor(t.block());
                float wx = tx * tilesize, wy = ty * tilesize;

                for(int i = 0; i < 4; i++){
                    int[] d = dirs[i];
                    Tile tile = world.tile(tx + d[0], ty + d[1]);
                    if(tile == null) continue;
                    if(tall(world.tile(tx + d[0], ty + d[1]))) continue; // buried edge

                    float mx = wx + d[0] * h, my = wy + d[1] * h;
                    if(d[0] * (cx - mx) + d[1] * (cy - my) <= 0) continue; // faces away

                    float ax = mx + d[1] * h, ay = my + d[0] * h;
                    float bx = mx - d[1] * h, by = my - d[0] * h;
                    float ax2 = cx + (ax - cx) * s, ay2 = cy + (ay - cy) * s;
                    float bx2 = cx + (bx - cx) * s, by2 = cy + (by - cy) * s;

                    float c = shade[i];
                    Tmp.c1.set(baseColor(t.block())).mul(c, c, c, 1f);
                    Draw.color(Tmp.c1);
                    Fill.quad(ax, ay, bx, by, bx2, by2, ax2, ay2);
                }
            }
        }
        Draw.color();
    }
}