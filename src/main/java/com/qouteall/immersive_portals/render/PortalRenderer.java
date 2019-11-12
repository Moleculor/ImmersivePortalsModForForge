package com.qouteall.immersive_portals.render;

import com.mojang.blaze3d.platform.GlStateManager;
import com.qouteall.immersive_portals.CGlobal;
import com.qouteall.immersive_portals.CHelper;
import com.qouteall.immersive_portals.my_util.Helper;
import com.qouteall.immersive_portals.portal.Portal;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ActiveRenderInfo;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.dimension.DimensionType;

import java.util.Comparator;
import java.util.List;
import java.util.Stack;
import java.util.function.Supplier;
import java.util.stream.Collectors;

public abstract class PortalRenderer {
    
    public static final Minecraft mc = Minecraft.getInstance();
    protected Supplier<Integer> maxPortalLayer = () -> CGlobal.maxPortalLayer;
    private Supplier<Double> portalRenderingRange = () -> 64.0;
    protected Stack<Portal> portalLayers = new Stack<>();
    
    //this WILL be called when rendering portal
    public abstract void onBeforeTranslucentRendering();
    
    //this WILL be called when rendering portal
    public abstract void onAfterTranslucentRendering();
    
    //this WILL be called when rendering portal
    public abstract void onRenderCenterEnded();
    
    //this will NOT be called when rendering portal
    public abstract void prepareRendering();
    
    //this will NOT be called when rendering portal
    public abstract void finishRendering();
    
    //this will be called when rendering portal entities
    public abstract void renderPortalInEntityRenderer(Portal portal);
    
    //0 for rendering outer world
    //1 for rendering world inside portal
    //2 for rendering world inside PortalEntity inside portal
    public int getPortalLayer() {
        return portalLayers.size();
    }
    
    public boolean isRendering() {
        return getPortalLayer() != 0;
    }
    
    public abstract boolean shouldSkipClearing();
    
    public Portal getRenderingPortal() {
        return portalLayers.peek();
    }
    
    public boolean shouldRenderPlayerItself() {
        return isRendering() &&
            mc.renderViewEntity.dimension == MyRenderHelper.originalPlayerDimension &&
            getRenderingPortal().canRenderEntityInsideMe(MyRenderHelper.originalPlayerPos);
    }
    
    public boolean shouldRenderEntityNow(Entity entity) {
//        if (OFHelper.getIsUsingShader()) {
//            if (Shaders.isShadowPass) {
//                return true;
//            }
//        }
        if (isRendering()) {
            return getRenderingPortal().canRenderEntityInsideMe(entity.getPositionVec());
        }
        return true;
    }
    
    protected void renderPortals() {
        assert mc.renderViewEntity.world == mc.world;
        assert mc.renderViewEntity.dimension == mc.world.dimension.getType();
        
        for (Portal portal : getPortalsNearbySorted()) {
            renderPortalIfRoughCheckPassed(portal);
        }
    }
    
    private void renderPortalIfRoughCheckPassed(Portal portal) {
        if (!portal.isPortalValid()) {
            Helper.err("rendering invalid portal " + portal);
            return;
        }
        
        //do not use last tick pos
        Vec3d thisTickEyePos = mc.renderViewEntity.getEyePosition(1);
        if (!portal.isInFrontOfPortal(thisTickEyePos)) {
            return;
        }
        
        if (isRendering()) {
            //avoid rendering reverse portal inside portal
            Portal outerPortal = portalLayers.peek();
            if (!outerPortal.canRenderPortalInsideMe(portal)) {
                return;
            }
        }
        
        doRenderPortal(portal);
    }
    
    private List<Portal> getPortalsNearbySorted() {
        Vec3d cameraPos = mc.renderViewEntity.getPositionVec();
        double range = 128.0;
        return CHelper.getClientNearbyPortals(range)
            .sorted(
                Comparator.comparing(portalEntity ->
                    portalEntity.getDistanceToNearestPointInPortal(cameraPos)
                )
            ).collect(Collectors.toList());
    }
    
    protected abstract void doRenderPortal(Portal portal);
    
    //it will overwrite the matrix
    protected final void manageCameraAndRenderPortalContent(
        Portal portal
    ) {
        if (getPortalLayer() > maxPortalLayer.get()) {
            return;
        }
    
        MyRenderHelper.onBeginPortalWorldRendering(portalLayers);
        
        Entity cameraEntity = mc.renderViewEntity;
        ActiveRenderInfo camera = mc.gameRenderer.getActiveRenderInfo();
        
        assert cameraEntity.world == mc.world;
        
        Vec3d oldPos = cameraEntity.getPositionVec();
        Vec3d oldLastTickPos = Helper.lastTickPosOf(cameraEntity);
        DimensionType oldDimension = cameraEntity.dimension;
        ClientWorld oldWorld = ((ClientWorld) cameraEntity.world);
    
        Vec3d oldCameraPos = camera.getProjectedView();
        
        Vec3d newPos = portal.applyTransformationToPoint(oldPos);
        Vec3d newLastTickPos = portal.applyTransformationToPoint(oldLastTickPos);
        DimensionType newDimension = portal.dimensionTo;
        ClientWorld newWorld =
            CGlobal.clientWorldLoader.getOrCreateFakedWorld(newDimension);
        //Vec3d newCameraPos = portal.applyTransformationToPoint(oldCameraPos);
        
        Helper.setPosAndLastTickPos(cameraEntity, newPos, newLastTickPos);
        cameraEntity.dimension = newDimension;
        cameraEntity.world = newWorld;
        mc.world = newWorld;
    
        renderPortalContentWithContextSwitched(
            portal, oldCameraPos
        );
        
        //restore the position
        cameraEntity.dimension = oldDimension;
        cameraEntity.world = oldWorld;
        mc.world = oldWorld;
        Helper.setPosAndLastTickPos(cameraEntity, oldPos, oldLastTickPos);
        
        //restore the transformation
        GlStateManager.enableDepthTest();
        GlStateManager.disableBlend();
        MyRenderHelper.setupCameraTransformation();
    }
    
    protected void renderPortalContentWithContextSwitched(
        Portal portal, Vec3d oldCameraPos
    ) {
        GlStateManager.enableAlphaTest();
        GlStateManager.enableCull();
        
        WorldRenderer worldRenderer = CGlobal.clientWorldLoader.getWorldRenderer(portal.dimensionTo);
        ClientWorld destClientWorld = CGlobal.clientWorldLoader.getOrCreateFakedWorld(portal.dimensionTo);
        
        Helper.checkGlError();
        
        CGlobal.myGameRenderer.renderWorld(
            MyRenderHelper.partialTicks, worldRenderer, destClientWorld, oldCameraPos
        );
        
        Helper.checkGlError();
        
    }
}