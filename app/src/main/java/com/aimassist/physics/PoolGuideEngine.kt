package com.aimassist.physics

import com.aimassist.model.*
import kotlin.math.*

class PoolGuideEngine(private val width: Float = 2000f, private val height: Float = 1000f) {
    fun calculate(balls: List<DetectedBall>, cueAngle: Float?): List<GuideLine> {
        val cue = balls.minByOrNull { (it.x - width*.25f).pow(2) + (it.y-height*.5f).pow(2) } ?: return emptyList()
        val a = cueAngle ?: return emptyList(); val dx = cos(a); val dy = sin(a)
        val end = rayToRail(Point2(cue.x,cue.y), dx, dy)
        val lines = mutableListOf(GuideLine(Point2(cue.x,cue.y), end, GuideLine.Kind.AIM))
        val target = balls.filter { it !== cue }.minByOrNull { distanceAlong(cue.x,cue.y,it.x,it.y,dx,dy) }
        if (target != null) lines += GuideLine(Point2(target.x,target.y), rayToRail(Point2(target.x,target.y), dx,dy), GuideLine.Kind.OBJECT)
        return lines
    }
    private fun distanceAlong(x:Float,y:Float,px:Float,py:Float,dx:Float,dy:Float)=max(0f,(px-x)*dx+(py-y)*dy)
    private fun rayToRail(p:Point2,dx:Float,dy:Float):Point2 { val ts=mutableListOf<Float>(); if(dx>0)ts+=((width-p.x)/dx) else if(dx<0)ts+=(-p.x/dx); if(dy>0)ts+=((height-p.y)/dy) else if(dy<0)ts+=(-p.y/dy); val t=ts.filter{it>0}.minOrNull()?:0f; return Point2(p.x+dx*t,p.y+dy*t) }
}
