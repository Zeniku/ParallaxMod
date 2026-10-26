package parallax;
import arc.*;
import arc.graphics.g2d.*;
import arc.math.geom.*;
import arc.util.*;
import arc.math.*;
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
    static float strength = 2f;

    // {layer, base k}
    //TEMP stuff 
    //basically layer map :D couldve use Objectmap or somethin but EH
    static final float[][] layers = {
        {Layer.block,      0.02f},
        {Layer.turret,     0.04f},
        {Layer.flyingUnit, 0.08f},
        {Layer.bullet,     0.10f},
        {Layer.effect,     0.12f},
    };

    // order matches dirs: left, right, bottom, top
    static final int[][] dirs = {{-1,0},{1,0},{0,-1},{0,1}};
    //block side "shades"
    static final float[] shade = {0.75f, 0.55f, 0.85f, 0.65f};
    static final Mat mat = new Mat();
    static final Mat savedCam = new Mat();

    @Override
    public void init(){
        Events.run(Trigger.draw, () -> {
            if(!enabled){
                Draw.trans(mat.idt());
                return;
            }

            float cx = Core.camera.position.x, cy = Core.camera.position.y;
            float blockK = layers[0][1] * strength ;

            Draw.draw(Layer.floor - 1f, () -> Draw.trans(mat.idt()));

            drawSides(cx, cy, blockK);

            // since blocks are like multiple layers
            scaleCamera(Layer.block - 0.501f, Layer.block + 0.001f, blockK);

            for(float[] l : layers){
                float z = l[0], k = l[1] * strength;
                Draw.draw(z - 0.001f, () -> {
                    mat.setToTranslation(cx, cy);
                    mat.scale(1f + k, 1f + k);
                    mat.translate(-cx, -cy);
                    Draw.trans(mat);
                });
            }

            // keep ui safe plsss
            Draw.draw(10000f, () -> Draw.trans(mat.idt()));
        });

        //PLSSSSS
        Events.run(Trigger.uiDrawBegin, () -> Draw.trans(mat.idt()));
    }
    
    // determine if block is tall
    static boolean tall(Tile t){
        return t != null && t.block() != Blocks.air && (t.block() instanceof Wall || t.block() instanceof StaticWall);
    }

    static void scaleCamera(float zFrom, float zTo, float k){
        float cx = Core.camera.position.x, cy = Core.camera.position.y;
        //start
        Draw.draw(zFrom, () -> {
            savedCam.set(Core.camera.mat);
            Core.camera.mat.translate(cx, cy).scale(1f + k, 1f + k).translate(-cx, -cy);
        });
        //reset
        Draw.draw(zTo, () -> Core.camera.mat.set(savedCam));
    }

    static void drawSides(float cx, float cy, float k){
        //side
        float s = 1f + k;

        float h = tilesize / 2f;

        Core.camera.bounds(Tmp.r1);
        int x0 = Math.max((int)(Tmp.r1.x / tilesize) - 2, 0);
        int y0 = Math.max((int)(Tmp.r1.y / tilesize) - 2, 0);
        int x1 = Math.min((int)((Tmp.r1.x + Tmp.r1.width) / tilesize) + 2, world.width() - 1);
        int y1 = Math.min((int)((Tmp.r1.y + Tmp.r1.height) / tilesize) + 2, world.height() - 1);

        // draw under walls / block pls?
        Draw.z(Layer.block - 0.003f);


        // might change it later
        for(int ty = y0; ty <= y1; ty++){
            for(int tx = x0; tx <= x1; tx++){
                Tile t = world.tile(tx, ty);
                if(!tall(t)) continue;

                float wx = tx * tilesize, wy = ty * tilesize;

                for(int i = 0; i < 4; i++){
                    int[] d = dirs[i]; // couldve use Geom.d4 or somethin
                    if(tall(world.tile(tx + d[0], ty + d[1]))) continue; // buried edge

                    float mx = wx + d[0] * h, my = wy + d[1] * h;
                    if(d[0] * (cx - mx) + d[1] * (cy - my) <= 0) continue; // faces away

                    float ax = mx + d[1] * h, ay = my + d[0] * h;
                    float bx = mx - d[1] * h, by = my - d[0] * h;
                    float ax2 = cx + (ax - cx) * s, ay2 = cy + (ay - cy) * s;
                    float bx2 = cx + (bx - cx) * s, by2 = cy + (by - cy) * s;

                    float c = shade[i];
                    Tmp.c1.set(t.block().mapColor).mul(c, c, c, 1f);
                    // Draws like mapcolor which doesnt exist
                    Draw.color(Tmp.c1);
                    //couldve shouldve use draw.rect now do i
                    Fill.quad(ax, ay, bx, by, bx2, by2, ax2, ay2);
                }
            }
        }
        Draw.color();
    }
}