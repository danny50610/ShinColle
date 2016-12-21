package com.lulan.shincolle.ai.path;

import javax.annotation.Nullable;

import com.lulan.shincolle.entity.IShipAttackBase;
import com.lulan.shincolle.entity.IShipNavigator;
import com.lulan.shincolle.utility.BlockHelper;
import com.lulan.shincolle.utility.EntityHelper;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLiving;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.ChunkCache;
import net.minecraft.world.World;

/**SHIP PATH NAVIGATE
 * ship or airplane限定path ai, 該entity必須實作IShipNavigator
 * 無視重力或者浮力作用找出空中 or 水中路徑, 若entity在陸上移動則不需要更新此navigator
 * update move的部份不採用自然墜落跟jump, 而是直接加上一個motionY
 * 注意此path navigator使用時, 必須把ship floating關閉以免阻礙y軸移動
 */
public class ShipPathNavigate
{
	private static int UpdatePathDelay = 20;
    private EntityLiving host;
    /** The entity using ship path navigate */
    private IShipNavigator hostShip;
    private World world;
    /** The PathEntity being followed. */
    @Nullable
    private ShipPath currentPath;
    private double speed;
    /** Time, in number of ticks, following the current path */
    private int pathTicks;
    /** The time when the last position check was done (to detect successful movement) */
    private int ticksAtLastPos;
    /** Coordinates of the entity's position last time a check was done (part of monitoring getting 'stuck') */
    private Vec3d lastPosCheck = Vec3d.ZERO;
    /** pos for stuck checking */
    private Vec3d lastPosStuck = Vec3d.ZERO;
    /** time vars for stuck checking */
    private long timeoutTimer = 0L;
    private long lastTimeoutCheck = 0L;
    private double timeoutLimit;
    /** 距離路徑點多近才會判定為到達該點, 預設0.5格 */
    private float maxDistanceToWaypoint = 0.5F;
    private int hostCeilWeight, hostCeilHight;
    private long lastTimeUpdated;
    private BlockPos targetPos;
    

    public ShipPathNavigate(EntityLiving entity, World world)
    {
        this.host = entity;
        this.hostShip = (IShipNavigator) entity;
        this.world = world;
        this.maxDistanceToWaypoint = this.host.width > 0.75F ? this.host.width * 0.5F : 0.75F - this.host.width * 0.5F;
        this.hostCeilWeight = MathHelper.ceil(this.host.width);
        this.hostCeilHight = MathHelper.ceil(this.host.height);
        
    }

    /**
     * Sets the speed
     */
    public void setSpeed(double par1)
    {
        this.speed = par1;
    }

    /**
     * Gets the maximum distance that the path finding will search in.
     */
    public float getPathSearchRange()
    {
    	if (host instanceof IShipAttackBase)
    	{
    		return 64F;
    	}
    	else
    	{
    		return 32F;
    	}
    }
    
    /**
     * Try to find and set a path to XYZ. Returns true if successful.
     */
    public boolean tryMoveToXYZ(double x, double y, double z, double speed)
    {
        ShipPath path = this.getPathToXYZ(MathHelper.floor(x), ((int)y), MathHelper.floor(z));
        return this.setPath(path, speed);
    }

    /**
     * Returns the path to the given coordinates
     */
    public ShipPath getPathToXYZ(double x, double y, double z)
    {
        return !this.canNavigate() ? null : this.getShipPathToXYZ(this.host, MathHelper.floor(x), (int)y, MathHelper.floor(z), this.getPathSearchRange(), this.hostShip.canFly());
    }
    
    public ShipPath getShipPathToXYZ(Entity entity, int x, int y, int z, float range, boolean canFly)
    {
    	//若path已經存在而且目標也相同, 則不重複找path
    	BlockPos pos = new BlockPos(x, y, z);
        if (this.currentPath != null && !this.currentPath.isFinished() && pos.equals(this.targetPos))
        {
            return this.currentPath;
        }
        
        this.targetPos = pos;
        this.world.theProfiler.startSection("pathfind");
        BlockPos hostPos = new BlockPos(entity);
        int i = (int)(range + 8.0F);
        ChunkCache chunkcache = new ChunkCache(this.world, hostPos.add(-i, -i, -i), hostPos.add(i, i, i), 0);
        ShipPath pathentity = (new ShipPathFinder(chunkcache, canFly)).findPath(entity, x, y, z, range);
        this.world.theProfiler.endSection();
        return pathentity;
    }

    /**
     * Returns the path to the given EntityLiving
     */
    public ShipPath getPathToEntityLiving(Entity entity)
    {
        return !this.canNavigate() ? null : this.getPathEntityToEntity(this.host, entity, this.getPathSearchRange(), this.hostShip.canFly());
    }
    
    public ShipPath getPathEntityToEntity(Entity entity, Entity targetEntity, float range, boolean canFly)
    {
    	//若path已經存在而且目標也相同, 則不重複找path
    	BlockPos pos = new BlockPos(targetEntity);
        if (this.currentPath != null && !this.currentPath.isFinished() && pos.equals(this.targetPos))
        {
            return this.currentPath;
        }
    	
        this.targetPos = pos;
        this.world.theProfiler.startSection("pathfind");
        BlockPos hostPos = (new BlockPos(entity)).up();
        int i = (int)(range + 16.0F);
        ChunkCache chunkcache = new ChunkCache(this.world, hostPos.add(-i, -i, -i), hostPos.add(i, i, i), 0);
        ShipPath pathentity = (new ShipPathFinder(chunkcache, canFly)).findPath(entity, targetEntity, range);
        this.world.theProfiler.endSection();
        return pathentity;
    }
    
    /**
     * Try to find and set a path to EntityLiving. Returns true if successful.
     */
    public boolean tryMoveToEntityLiving(Entity entity, double speed)
    {
        ShipPath pathentity = this.getPathToEntityLiving(entity);
        return pathentity != null ? this.setPath(pathentity, speed) : false;
    }

    /**
     * sets the active path data if path is 100% unique compared to old path, checks to adjust path for sun avoiding
     * ents and stores end coords
     */
    public boolean setPath(ShipPath pathEntity, double speed)
    {
        //若路徑為null, 表示找不到路徑
    	if (pathEntity == null)
    	{
            this.currentPath = null;
            return false;
        }
        else
        {
        	//比較新舊路徑是否相同, 不同時將舊路徑蓋掉
            if (!pathEntity.isSamePath(this.currentPath))
            {
                this.currentPath = pathEntity;
            }
            
            //若路徑長度為0, 表示沒path
            if (this.currentPath.getCurrentPathLength() == 0)
            {
                return false;
            }
            //成功設定path
            else
            {
                this.speed = speed;
                Vec3d vec3 = this.getEntityPosition();
                this.ticksAtLastPos = this.pathTicks;
                this.lastPosCheck = vec3;
                return true;
            }
        }
    }

    /**
     * gets the actively used PathEntity
     */
    public ShipPath getPath()
    {
        return this.currentPath;
    }

    /** navigation tick */
    public void onUpdateNavigation()
    {
    	//wait for entity init
    	if (host.ticksExisted > 40)
    	{
    		++this.pathTicks;
    		
            //若有path
            if (!this.noPath())
            {
            	//若可以執行移動, 則跑pathFollow方法更新下一個目標點
                if (this.canNavigate())
                {
                    this.pathFollow();
                }
                
                if (!this.noPath())
                {
                    Vec3d vec3 = this.currentPath.getPosition(this.host);
                    
                    if (vec3 != null)
                    {
                    	//new method 1.9.4, move to target block with entity width and block's height (like slab or stair)
                        BlockPos blockPos = (new BlockPos(vec3)).down();
                        AxisAlignedBB blockAABB = this.world.getBlockState(blockPos).getBoundingBox(this.world, blockPos);
                        vec3 = vec3.subtract(0.0D, 1.0D - blockAABB.maxY, 0.0D);
                        this.hostShip.getShipMoveHelper().setMoveTo(vec3.xCoord, vec3.yCoord + 0.1D, vec3.zCoord, this.speed);
                    }
                }
            }
    	}
            
    }

    /** 判定entity是否卡住(超過100 tick仍在原地) or entity是否可以抄捷徑以省略一些路徑點 
     *  以y高度判定是否有些點可以省略 (不判定水平距離)
     */
    private void pathFollow()
    {
        Vec3d hostPos = this.getEntityPosition();
        int i = this.currentPath.getCurrentPathLength();

        //找出y高度不同於host所在高度的點
        for (int j = this.currentPath.getCurrentPathIndex(); j < this.currentPath.getCurrentPathLength(); ++j)
        {
            if ((double)this.currentPath.getPathPointFromIndex(j).yCoord != Math.floor(hostPos.yCoord))
            {
                i = j;
                break;
            }
        }

        Vec3d nowPos = this.currentPath.getCurrentPos();

        //若host成功到達目標路徑點, 則目標繼續設為下一個路徑點
        if (MathHelper.abs((float)(this.host.posX - (nowPos.xCoord + 0.5D))) < this.maxDistanceToWaypoint && MathHelper.abs((float)(this.host.posZ - (nowPos.zCoord + 0.5D))) < this.maxDistanceToWaypoint)
        {
            this.currentPath.setCurrentPathIndex(this.currentPath.getCurrentPathIndex() + 1);
        }

        //若有y高度不同的點, 從該點往回到host目前點, 找其中是否有能直接前進的點
        for (int j1 = i - 1; j1 >= this.currentPath.getCurrentPathIndex(); --j1)
        {
            if (this.isDirectPathBetweenPoints(hostPos, this.currentPath.getVectorFromIndex(this.host, j1), this.hostCeilWeight, this.hostCeilHight, this.hostCeilWeight))
            {
                this.currentPath.setCurrentPathIndex(j1);
                break;
            }
        }

        this.checkForStuck(hostPos);
    }
    
    /**
     * clear path if entity is stuck
     */
    protected void checkForStuck(Vec3d pos)
    {
    	int checkTick = this.pathTicks - this.ticksAtLastPos;
    	
    	//每卡住32 tick檢查一次
    	if (checkTick % 32 == 0)
        {
    		boolean isStuck = false;
    		double dist = pos.squareDistanceTo(this.lastPosCheck);
    		
        	if(dist < 2.25D)
        	{
        		isStuck = true;
        		
        		//嘗試跳躍 + 左右隨機移動來脫離卡住狀態
            	if (!currentPath.isFinished())
            	{
            		float dx = (float) (currentPath.getVectorFromIndex(this.host, currentPath.getCurrentPathIndex()).xCoord - host.posX);
                	float dz = (float) (currentPath.getVectorFromIndex(this.host, currentPath.getCurrentPathIndex()).zCoord - host.posZ);
                	double targetX = 0D;
                	double targetZ = 0D;
                	
                	//get random position
                	if (dx > 0.1F || dx < -0.1F)  //若目標點離x方向一定距離, 則在z方向隨機選+-1
                	{
                		targetZ = host.getRNG().nextInt(2) == 0 ? -1D : 1D;
                	}
                	
                	if (dz > 0.1F || dz < -0.1F)  //若目標點離z方向一定距離, 則在x方向隨機選+-1
                	{
                		targetX = host.getRNG().nextInt(2) == 0 ? -1D : 1D;
                	}
                	
                	//set move
                	this.host.motionX = this.speed * 0.5D * targetX;
                	this.host.motionZ = this.speed * 0.5D * targetZ;
                	
                	//try random jump
                	if (host.getRNG().nextInt(2) == 0)
                	{
                		host.getJumpHelper().setJumping();
                		float speed = (float) host.getAIMoveSpeed() * 0.35F;
                		if(dx > 0.2F) host.motionX += speed;
                		if(dx < 0.2F) host.motionX -= speed;
                		if(dz > 0.2F) host.motionZ += speed;
                		if(dz < 0.2F) host.motionZ -= speed;
                	}
            	}
        	}
        	
    		//reset tick every 96 ticks
            if (checkTick > 100)
            {
            	if(isStuck)
            	{
            		this.clearPathEntity();
            	}
                
                this.ticksAtLastPos = this.pathTicks;
            }
            
            //update pos check
            this.lastPosCheck = pos;
        }

    	//若path沒被清除, 且還沒走到終點
        if (this.currentPath != null && !this.currentPath.isFinished())
        {
            Vec3d vec3d = this.currentPath.getCurrentPos();

            //若目標點跟上一次檢查點lastPosStuck不同, 則更新之, 並以host速度設定下一次檢查時間
            if (!vec3d.equals(this.lastPosStuck))
            {
                this.lastPosStuck = vec3d;
                double d0 = pos.distanceTo(this.lastPosStuck);
                this.timeoutLimit = this.host.getAIMoveSpeed() > 0F ? d0 / (double)this.host.getAIMoveSpeed() * 1000D : 0D;
            }
            //若lastPosStuck相同, 表示目標點沒換過, 持續計時
            else
            {
                this.timeoutTimer += System.currentTimeMillis() - this.lastTimeoutCheck;
            }

            //若host已經走了超過預定時間三倍, 則清空path
            if (this.timeoutLimit > 0D && (double)this.timeoutTimer > this.timeoutLimit * 3D)
            {
                this.lastPosStuck = Vec3d.ZERO;
                this.timeoutTimer = 0L;
                this.timeoutLimit = 0D;
                this.clearPathEntity();
            }

            this.lastTimeoutCheck = System.currentTimeMillis();
        }
    }

    /**
     * If null path or reached the end
     */
    public boolean noPath()
    {
        return this.currentPath == null || this.currentPath.isFinished();
    }

    /**
     * sets active PathEntity to null
     */
    public void clearPathEntity()
    {
        this.currentPath = null;
    }

    /** 
     * 將entity位置資訊以vec3表示
     */
    private Vec3d getEntityPosition()
    {
        return new Vec3d(this.host.posX, this.host.posY + 0.5D, this.host.posZ);
    }

    /**
     * 若能飛, 則以目前y為起點
     * 若不能飛, 則往下找到第一個非空氣的方塊為起點 (水面 or 實體方塊)
     */
    private double getPathableYPos()
    {
    	if (this.hostShip.canFly())
    	{
//    	LogHelper.info("DEBUG : path navi: path y "+(int)(this.theEntity.boundingBox.minY + 0.5D));
    		//可以飛, 直接以目前y為起點
//            return (int)(this.theEntity.boundingBox.minY + 0.5D);
    		return this.host.posY;
        }
        else
        {
        	 int i = (int) this.host.posY;
             IBlockState block = this.world.getBlockState(new BlockPos(MathHelper.floor(this.host.posX), i, MathHelper.floor(this.host.posZ)));
             int j = 0;
             
             //往下找出第一個非air的方塊, 若是液體方塊則回傳稍微高一點的y
             do
             {
                 if (block != null && block.getMaterial() != Material.AIR)
                 {
                	 if (BlockHelper.checkBlockIsLiquid(block))
                	 {
                		 return i + 0.4D;
                	 }
                	 else
                	 {
                		 return i;
                	 }  
                 }

                 ++i;
                 block = this.world.getBlockState(new BlockPos(MathHelper.floor(this.host.posX), i, MathHelper.floor(this.host.posZ)));
                 ++j;
             }
             while (j <= 24);	//最多往下找24格就停止
             
             //找超過16格都沒底, 則直接回傳目前y
             return (int) this.host.posY;
        }
    }

    /**非騎乘中, 且該entity可以飛 or 站在地面 or 在可穿透方塊中
     */
    private boolean canNavigate()
    {
        return !host.isRiding() && (this.hostShip.canFly() || this.host.onGround || EntityHelper.checkEntityIsFree(host));
    }

    /**
     * Returns true if the entity is in water or lava or other liquid
     */
    private boolean isInLiquid()
    {
        return EntityHelper.checkEntityIsInLiquid(host);
    }

    /**entity抄捷徑的方法, 若直線可視無阻礙, 則會直線往該點移動
     * 
     * 1.9.4
     * 直接視線判定目標是否有無被阻擋
     * 
     ********** 以下為1.7.10* ***********
     * NEW: 加入y軸判定, 使其可以抄y軸捷徑
     * Returns true when an entity of specified size could safely walk in a straight line between the two points. Args:
     * pos1, pos2, entityXSize, entityYSize, entityZSize
     */
    private boolean isDirectPathBetweenPoints(Vec3d pos1, Vec3d pos2, int xSize, int ySize, int zSize)
    {
    	RayTraceResult raytraceresult = this.world.rayTraceBlocks(pos1, new Vec3d(pos2.xCoord, pos2.yCoord + this.host.height * 0.5D, pos2.zCoord), false, true, false);
        return raytraceresult == null || raytraceresult.typeOfHit == RayTraceResult.Type.MISS;
    }

    /**
     * Returns true when an entity could stand at a position, including solid blocks under the entire entity. Args:
     * xOffset, yOffset, zOffset, entityXSize, entityYSize, entityZSize, originPosition, vecX, vecZ
     */
    private boolean isSafeToStandAt(int xOffset, int yOffset, int zOffset, int xSize, int ySize, int zSize, Vec3d orgPos, double vecX, double vecZ)
    {
    	//會飛的entity不須檢查落腳點
    	if (this.hostShip.canFly()) return true;
    	
        int xSize2 = xOffset - xSize / 2;
        int zSize2 = zOffset - zSize / 2;
        
        //若該位置有方塊卡住, 則false
        if (!this.isPositionClear(xSize2, yOffset, zSize2, xSize, ySize, zSize, orgPos, vecX, vecZ))
        {
            return false;
        }
        //檢查所有落腳方塊是否安全可站立
        else
        {
            for (int x1 = xSize2; x1 < xSize2 + xSize; ++x1)
            {
                for (int z1 = zSize2; z1 < zSize2 + zSize; ++z1)
                {
                    double x2 = x1 + 0.5D - orgPos.xCoord;
                    double z2 = z1 + 0.5D - orgPos.zCoord;

                    //檢查底下方塊種類
                    if (x2 * vecX + z2 * vecZ >= 0D)
                    {
                        IBlockState block = this.world.getBlockState(new BlockPos(x1, yOffset - 1, z1));
                        //若不能飛, 底下又是air, 則false
                        if(block == null || block.getMaterial() == Material.AIR)
                        {
                            return false;
                        }
                    }
                }
            }

            return true;
        }
    }

    /**
     * Returns true if an entity does not collide with any solid blocks at the position. Args: xOffset, yOffset,
     * zOffset, entityXSize, entityYSize, entityZSize, originPosition, vecX, vecZ
     */
    private boolean isPositionClear(int xOffset, int yOffset, int zOffset, int xSize, int ySize, int zSize, Vec3d orgPos, double vecX, double vecZ)
    {
    	//考慮host大小後, 檢查host身體佔據的範圍是否有方塊是實體不能通過
        for (BlockPos blockpos : BlockPos.getAllInBox(new BlockPos(xOffset, yOffset, zOffset), new BlockPos(xOffset + xSize - 1, yOffset + ySize - 1, zOffset + zSize - 1)))
        {
            double d0 = (double)blockpos.getX() + 0.5D - orgPos.xCoord;
            double d1 = (double)blockpos.getZ() + 0.5D - orgPos.zCoord;

            if (d0 * vecX + d1 * vecZ >= 0D)
            {
                Block block = this.world.getBlockState(blockpos).getBlock();

                if (!block.isPassable(this.world, blockpos))
                {
                    return false;
                }
            }
        }

        return true;
    }
}
