package com.lulan.shincolle.entity;

import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import javax.annotation.Nullable;

import com.lulan.shincolle.ShinColle;
import com.lulan.shincolle.ai.EntityAIShipAttackOnCollide;
import com.lulan.shincolle.ai.EntityAIShipFlee;
import com.lulan.shincolle.ai.EntityAIShipFloating;
import com.lulan.shincolle.ai.EntityAIShipFollowOwner;
import com.lulan.shincolle.ai.EntityAIShipGuarding;
import com.lulan.shincolle.ai.EntityAIShipLookIdle;
import com.lulan.shincolle.ai.EntityAIShipOpenDoor;
import com.lulan.shincolle.ai.EntityAIShipRangeTarget;
import com.lulan.shincolle.ai.EntityAIShipRevengeTarget;
import com.lulan.shincolle.ai.EntityAIShipSit;
import com.lulan.shincolle.ai.EntityAIShipWander;
import com.lulan.shincolle.ai.EntityAIShipWatchClosest;
import com.lulan.shincolle.ai.path.ShipMoveHelper;
import com.lulan.shincolle.ai.path.ShipPathNavigate;
import com.lulan.shincolle.capability.CapaShipInventory;
import com.lulan.shincolle.capability.CapaShipSavedValues;
import com.lulan.shincolle.capability.CapaTeitoku;
import com.lulan.shincolle.client.gui.inventory.ContainerShipInventory;
import com.lulan.shincolle.client.render.IShipCustomTexture;
import com.lulan.shincolle.crafting.EquipCalc;
import com.lulan.shincolle.crafting.ShipCalc;
import com.lulan.shincolle.entity.other.EntityAbyssMissile;
import com.lulan.shincolle.entity.other.EntityRensouhou;
import com.lulan.shincolle.handler.ConfigHandler;
import com.lulan.shincolle.init.ModBlocks;
import com.lulan.shincolle.init.ModItems;
import com.lulan.shincolle.init.ModSounds;
import com.lulan.shincolle.item.IShipCombatRation;
import com.lulan.shincolle.item.IShipFoodItem;
import com.lulan.shincolle.item.OwnerPaper;
import com.lulan.shincolle.item.PointerItem;
import com.lulan.shincolle.network.C2SInputPackets;
import com.lulan.shincolle.network.S2CEntitySync;
import com.lulan.shincolle.network.S2CGUIPackets;
import com.lulan.shincolle.network.S2CInputPackets;
import com.lulan.shincolle.network.S2CSpawnParticle;
import com.lulan.shincolle.proxy.CommonProxy;
import com.lulan.shincolle.proxy.ServerProxy;
import com.lulan.shincolle.reference.ID;
import com.lulan.shincolle.reference.Values;
import com.lulan.shincolle.utility.BlockHelper;
import com.lulan.shincolle.utility.CalcHelper;
import com.lulan.shincolle.utility.EntityHelper;
import com.lulan.shincolle.utility.FormationHelper;
import com.lulan.shincolle.utility.LogHelper;
import com.lulan.shincolle.utility.ParticleHelper;
import com.lulan.shincolle.utility.TargetHelper;
import com.lulan.shincolle.utility.TeamHelper;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityAgeable;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.passive.EntityTameable;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Items;
import net.minecraft.init.MobEffects;
import net.minecraft.init.SoundEvents;
import net.minecraft.item.Item;
import net.minecraft.item.ItemFood;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.DamageSource;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumHand;
import net.minecraft.util.SoundEvent;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.EnumSkyBlock;
import net.minecraft.world.World;
import net.minecraftforge.common.ForgeChunkManager;
import net.minecraftforge.common.ForgeChunkManager.Ticket;
import net.minecraftforge.fml.common.network.NetworkRegistry.TargetPoint;
import net.minecraftforge.fml.common.network.internal.FMLNetworkHandler;

/**SHIP DATA <br>
 * Explanation in crafting/ShipCalc.class
 */
public abstract class BasicEntityShip extends EntityTameable implements IShipCannonAttack, IShipGuardian, IShipFloating, IShipCustomTexture
{

	protected CapaShipInventory itemHandler;
	protected ShipPathNavigate shipNavigator;	//水空移動用navigator
	protected ShipMoveHelper shipMoveHelper;
	protected EntityLivingBase aiTarget;		//target for AI
	protected Entity guardedEntity;				//guarding target
	protected Entity atkTarget;					//attack target
	protected Entity rvgTarget;					//revenge target
	
	//for AI calc
	protected double ShipDepth;			//水深, 用於水中高度判定
	protected double ShipPrevX;			//ship posX 5 sec ago
	protected double ShipPrevY;			//ship posY 5 sec ago
	protected double ShipPrevZ;			//ship posZ 5 sec ago
	/** equip states: 0:HP 1:ATK 2:DEF 3:SPD 4:MOV 5:HIT 6:ATK_Heavy 7:ATK_AirLight 8:ATK_AirHeavy*/
	protected float[] StateEquip;
	/** final states: 0:HP 1:ATK 2:DEF 3:SPD 4:MOV 5:HIT 6:ATK_Heavy 7:ATK_AirLight 8:ATK_AirHeavy*/
	protected float[] StateFinal;
	/** final states before buffs: 0:HP 1:ATK 2:DEF 3:SPD 4:MOV 5:HIT 6:ATK_Heavy 7:ATK_AirLight 8:ATK_AirHeavy*/
	protected float[] StateFinalBU;
	/** minor states: 0:ShipLevel 1:Kills 2:ExpCurrent 3:ExpNext 4:NumAmmoLight 
	 *  5:NumAmmoHeavy 6:NumGrudge 7:NumAirLight 8:NumAirHeavy 9:
	 *  10:followMin 11:followMax 12:FleeHP 13:TargetAIType 14:guardX 15:guardY 16:guardZ 17:guardDim
	 *  18:guardID 19:shipType 20:shipClass 21:playerUID 22:shipUID 23:playerEID 24:guardType 
	 *  25:damageType 26:formationType 27:formationPos 28:grudgeConsumption 29:ammoConsumption
	 *  30:morale 31:Saturation 32:MaxSaturation 33:hitHeight 34:HitAngle 35:SensBody 36:InvSize
	 *  37:ChunkLoaderLV 38:FlareLV 39:SearchlightLV 40:LastX 41:LastY 42:LastZ 43:CraneState
	 *  44:WpStayTime
	 */
	protected int[] StateMinor;
	/** timer array: 0:RevengeTime 1:CraneTime 2:ImmuneTime 3:CraneDelay 4:WpStayTime 5:Emotion3Time
	 *               6:sound cd 7:FaceTick 8:HeadTilt 9:MoraleTime 10:EmoteDelay 11:LastCombatTime
	 *               12:
	 */
	/** EmotionTicks: 0:FaceTick 1:HeadTiltTick 2:AttackEmoCount 3:MoraleTick 4:EmoParticle CD
	 *                5:LastAttackTime */
	protected int[] StateTimer;
	/** equip effect: 0:critical 1:doubleHit 2:tripleHit 3:baseMiss 4:atk_AntiAir 5:atk_AntiSS 6:dodge*/
	protected float[] EffectEquip;
	/** equip effect: 0:critical 1:doubleHit 2:tripleHit 3:baseMiss 4:atk_AntiAir 5:atk_AntiSS 6:dodge*/
	protected float[] EffectEquipBU;
	/** formation effect: 0:ATK_L 1:ATK_H 2:ATK_AL 3:ATK_AH 4:DEF 5:MOV 6:MISS 7:DODGE 8:CRI
	 *                    9:DHIT 10:THIT 11:AA 12:ASM */
	protected float[] EffectFormation;
	/** formation fixed effect: 0:MOV */
	protected float[] EffectFormationFixed;
	/** EntityState: 0:State 1:Emotion 2:Emotion2 3:HP State 4:State2 5:AttackPhase 6:Emotion3*/
	protected byte[] StateEmotion;
	/** EntityFlag: 0:canFloatUp 1:isMarried 2:noFuel 3:canMelee 4:canAmmoLight 5:canAmmoHeavy 
	 *  6:canAirLight 7:canAirHeavy 8:headTilt(client only) 9:canRingEffect 10:canDrop 11:canFollow
	 *  12:onSightChase 13:AtkType_Light 14:AtkType_Heavy 15:AtkType_AirLight 16:AtkType_AirHeavy 
	 *  17:HaveRingEffect 18:PVPFirst 19:AntiAir 20:AntiSS 21:PassiveAI 22:TimeKeeper 23:PickItem 
	 */
	protected boolean[] StateFlag;
	/** BonusPoint: 0:HP 1:ATK 2:DEF 3:SPD 4:MOV 5:HIT */
	protected byte[] BonusPoint;
	/** TypeModify: 0:HP 1:ATK 2:DEF 3:SPD 4:MOV 5:HIT */
	protected float[] TypeModify;
	/** ModelPos: posX, posY, posZ, scale (in ship inventory) */
	protected float[] ModelPos;
	/** Update Flag: 0:FormationBuff */
	protected boolean[] UpdateFlag;
	/** waypoints: 0:last wp */
	protected BlockPos[] waypoints;
	/** owner name */
	public String ownerName;
	
	//for model render
	protected float[] rotateAngle;		//模型旋轉角度, 用於手持物品render
	
	//for initial
	private boolean initAI, initWaitAI;	//init flag
	private boolean isUpdated;			//updated ship id/owner id tag
	private int updateTime = 16;		//update check interval
	
	//chunk loader
	private Ticket chunkTicket;
	private Set<ChunkPos> chunks;
	
	//for debug
	public static boolean stopAI = false;  //stop onUpdate, onLivingUpdate
	
	
	public BasicEntityShip(World world)
	{
		super(world);
		this.ignoreFrustumCheck = true;  //即使不在視線內一樣render
		this.maxHurtResistantTime = 2;   //受傷無敵時間降為2 ticks
		this.ownerName = "";

		//init value
		this.itemHandler = new CapaShipInventory(CapaShipInventory.SlotMax, this);
		this.isImmuneToFire = true;	//set ship immune to lava
		this.StateEquip = new float[] {0F, 0F, 0F, 0F, 0F, 0F, 0F, 0F, 0F};
		this.StateFinal = new float[] {0F, 0F, 0F, 0F, 0F, 0F, 0F, 0F, 0F};
		this.StateFinalBU = this.StateFinal.clone();
		this.StateMinor = new int[] {1,  0,  0,  40, 0,
				                     0,  0,  0,  0,  0,
				                     3,  12, 35, 1,  -1,
				                     -1, -1, 0,  -1, 0,
				                     0,  -1, -1, -1, 0,
				                     0,  0,  0,  0,  0,
				                     60, 0,  10, 0,  0,
				                     -1, 0,  0,  0,  0,
				                     -1, -1, -1, 0,  0
				                    };
		this.StateTimer = new int[] {0, 0, 0, 0, 0,
									 0, 0, 0, 0, 0,
									 0, 0};
		this.EffectEquip = new float[] {0F, 0F, 0F, 0F, 0F, 0F, 0F};
		this.EffectEquipBU = this.EffectEquip.clone();
		this.EffectFormation = new float[] {0F, 0F, 0F, 0F, 0F,
									        0F, 0F, 0F, 0F, 0F,
									        0F, 0F, 0F};
		this.EffectFormationFixed = new float[] {0F};
		this.StateEmotion = new byte[] {0, 0, 0, 0, 0, 0, 0};
		this.StateFlag = new boolean[] {false, false, false, false, true,
				                        true, true, true, false, true,
								        true, false, true, true, true,
								        true, true, false, true, false,
								        false, false, true, true, false
								       };
		this.UpdateFlag = new boolean[] {false};
		this.BonusPoint = new byte[] {0, 0, 0, 0, 0, 0};
		this.TypeModify = new float[] {1F, 1F, 1F, 1F, 1F, 1F};
		this.ModelPos = new float[] {0F, 0F, 0F, 50F};
		this.waypoints = new BlockPos[] {BlockPos.ORIGIN};
		
		//for AI
		this.ShipDepth = 0D;			//water block above ship (within ship position)
		this.ShipPrevX = posX;			//ship position 5 sec ago
		this.ShipPrevY = posY;
		this.ShipPrevZ = posZ;
		this.stepHeight = 1F;
		
		//for render
		this.rotateAngle = new float[] {0F, 0F, 0F};		//model rotate angle (right hand)
		
		//for init
		this.initAI = false;			//normal init
		this.initWaitAI = false;		//slow init after player entity loaded
		this.isUpdated = false;
		
		//chunk loader
		this.chunkTicket = null;
		this.chunks = null;
		
		//choice random sensitive body part
		randomSensitiveBody();
		
	}
	
	@Override
	protected boolean canDespawn()
    {
        return false;
    }
	
	@Override
	public boolean isEntityInvulnerable(DamageSource source)
	{
        return StateTimer[ID.T.ImmuneTime] > 0;
    }
	
	@Override
	public boolean isBurning()
	{	//display fire effect
		return this.getStateEmotion(ID.S.HPState) == ID.HPState.HEAVY;
	}
	
	@Override
	public boolean isJumping()
	{
		return this.isJumping;
	}
	
	@Override
	public float getEyeHeight()
	{
		return this.height * 0.8F;
	}
	
    /**
     * Returns true if this thing is named
     */
	@Override
    public boolean hasCustomName()
    {
		if (ConfigHandler.showTag)
		{
			return super.hasCustomName();
		}
		
		return false;
    }
	
	/** init values, called in the end of constructor */
	protected void postInit()
	{
		this.shipNavigator = new ShipPathNavigate(this, world);
		this.shipMoveHelper = new ShipMoveHelper(this, 60F);
		this.initTypeModify();
	}
	
	//check world time is 0~23 hour, -1 for fail
	private int getWorldHourTime()
	{
		long time = this.world.provider.getWorldTime();
    	int checkTime = (int) (time % 1000L);
    	
    	if (checkTime == 0)
    	{
    		return (int) (time / 1000L) % 24;
    	}
    	
    	return -1;
	}
	
	/**
	 * type: 0:idle, 1:hit, 2:hurt, 3:dead, 4:marry, 5:knockback, 6:item, 7:feed, 10~33:timekeep
	 */
    @Nullable
    public static SoundEvent getCustomSound(int type, BasicEntityShip ship)
    {
    	//get custom sound rate
		int key = ship.getShipClass() + 2;
		float[] rate = ConfigHandler.configSound.SOUNDRATE.get(key);
		int typeKey = key * 100 + type;
		int typeTemp = type;
		
		//if timekeeping sound
		if (type >= 10 && type <= 33)
		{
			type = 8;
		}
		
		//has custom sound
		if (rate != null && rate[type] > 0.01F)
		{
			SoundEvent sound = ModSounds.CUSTOM_SOUND.get(typeKey);
			
			if (sound != null && ship.rand.nextFloat() < rate[type])
			{
				return sound;
			}
		}
		
		//no custom sound, return default sound
		switch (typeTemp)
		{
		case 0:
			return ModSounds.SHIP_IDLE;
		case 1:
			return ModSounds.SHIP_HIT;
		case 2:
			return ModSounds.SHIP_HURT;
		case 3:
			return ModSounds.SHIP_DEATH;
		case 4:
			return ModSounds.SHIP_MARRY;
		case 5:
			return ModSounds.SHIP_KNOCKBACK;
		case 6:
			return ModSounds.SHIP_ITEM;
		case 7:
			return ModSounds.SHIP_FEED;
		case 10:
			return ModSounds.SHIP_TIME0;
		case 11:
			return ModSounds.SHIP_TIME1;
		case 12:
			return ModSounds.SHIP_TIME2;
		case 13:
			return ModSounds.SHIP_TIME3;
		case 14:
			return ModSounds.SHIP_TIME4;
		case 15:
			return ModSounds.SHIP_TIME5;
		case 16:
			return ModSounds.SHIP_TIME6;
		case 17:
			return ModSounds.SHIP_TIME7;
		case 18:
			return ModSounds.SHIP_TIME8;
		case 19:
			return ModSounds.SHIP_TIME9;
		case 20:
			return ModSounds.SHIP_TIME10;
		case 21:
			return ModSounds.SHIP_TIME11;
		case 22:
			return ModSounds.SHIP_TIME12;
		case 23:
			return ModSounds.SHIP_TIME13;
		case 24:
			return ModSounds.SHIP_TIME14;
		case 25:
			return ModSounds.SHIP_TIME15;
		case 26:
			return ModSounds.SHIP_TIME16;
		case 27:
			return ModSounds.SHIP_TIME17;
		case 28:
			return ModSounds.SHIP_TIME18;
		case 29:
			return ModSounds.SHIP_TIME19;
		case 30:
			return ModSounds.SHIP_TIME20;
		case 31:
			return ModSounds.SHIP_TIME21;
		case 32:
			return ModSounds.SHIP_TIME22;
		case 33:
			return ModSounds.SHIP_TIME23;
		}
		
		return null;
    }
    
    //音量大小
    @Override
	protected float getSoundVolume()
    {
        return ConfigHandler.volumeShip;
    }
	
    @Override
    @Nullable
    protected SoundEvent getAmbientSound()
    {
		return getCustomSound(0, this);
    }
    
    @Override
    @Nullable
    protected SoundEvent getHurtSound()
    {
    	return getCustomSound(2, this);
    }
    
    @Override
    @Nullable
    protected SoundEvent getDeathSound()
    {
        return getCustomSound(3, this);
    }
	
    /**
     * Plays living's sound at its position
     */
	@Override
    public void playLivingSound()
    {
		//40% play sound
		if (this.rand.nextInt(10) > 4) return;
		
		//get sound event
		SoundEvent sound = null;
		
		//married ship
		if (this.getStateFlag(ID.F.IsMarried))
		{
			if (rand.nextInt(5) == 0)
			{
				sound = getCustomSound(4, this);
			}
			else
			{
				sound = getAmbientSound();
			}
		}
		//normal ship
		else
		{
			sound = getAmbientSound();
		}
		
		//play sound
		if (sound != null)
		{
			this.playSound(sound, this.getSoundVolume(), this.getSoundPitch());
		}
    }
	
	//play hurt sound with sound cooldown
	@Override
    protected void playHurtSound(DamageSource source)
    {
    	if (this.StateTimer[ID.T.SoundTime] <= 0)
    	{
    		this.StateTimer[ID.T.SoundTime] = 20 + this.getRNG().nextInt(30);
    		super.playHurtSound(source);
    	}
    }
	
	//timekeeping method
	protected void playTimeSound(int hour)
	{
		SoundEvent sound = this.getCustomSound(hour + 10, this);

		//play sound
		if (sound != null)
		{
			this.playSound(sound, this.getSoundVolume(), (this.rand.nextFloat() - this.rand.nextFloat()) * 0.1F + 1F);
		}
	}

    //get model rotate angle, par1 = 0:X, 1:Y, 2:Z
    @Override
    public float getModelRotate(int par1)
    {
    	switch (par1)
    	{
    	default:
    		return this.rotateAngle[0];
    	case 1:
    		return this.rotateAngle[1];
    	case 2:
    		return this.rotateAngle[2];
    	}
    }
    
    //set model rotate angle, par1 = 0:X, 1:Y, 2:Z
    @Override
	public void setModelRotate(int par1, float par2)
    {
		switch (par1)
		{
    	default:
    		rotateAngle[0] = par2;
    	case 1:
    		rotateAngle[1] = par2;
    	case 2:
    		rotateAngle[2] = par2;
    	}
	}

	@Override
	public EntityAgeable createChild(EntityAgeable entity)
	{
		return null;
	}

	//setup AI
	protected void setAIList()
	{
		//high priority
		this.tasks.addTask(1, new EntityAIShipSit(this));				//0111
		this.tasks.addTask(2, new EntityAIShipFlee(this));				//0111
		this.tasks.addTask(3, new EntityAIShipGuarding(this));			//0111
		this.tasks.addTask(4, new EntityAIShipFollowOwner(this));		//0111
		this.tasks.addTask(5, new EntityAIShipOpenDoor(this, true));	//0000
		
		//use melee attack
		if (this.getStateFlag(ID.F.UseMelee))
		{
			this.tasks.addTask(10, new EntityAIShipAttackOnCollide(this, 1D));//0100
		}
		
		//idle AI
		this.tasks.addTask(23, new EntityAIShipFloating(this));			//0111
		this.tasks.addTask(24, new EntityAIShipWatchClosest(this, EntityPlayer.class, 4F, 0.06F));//0010
		this.tasks.addTask(25, new EntityAIShipWander(this, 10, 5, 0.8D));//0111
		this.tasks.addTask(25, new EntityAIShipLookIdle(this));			//0011
	}
	
	//setup target AI
	public void setAITargetList()
	{
		//passive target AI
		if (this.getStateFlag(ID.F.PassiveAI))
		{
			this.targetTasks.addTask(1, new EntityAIShipRevengeTarget(this));
		}
		//active target AI
		else
		{
			this.targetTasks.addTask(1, new EntityAIShipRevengeTarget(this));
			this.targetTasks.addTask(5, new EntityAIShipRangeTarget(this, Entity.class));
		}
	}

	//clear AI
	protected void clearAITasks()
	{
		tasks.taskEntries.clear();
	}
	
	//clear target AI
	protected void clearAITargetTasks()
	{
		this.setAttackTarget(null);
		this.setEntityTarget(null);
		targetTasks.taskEntries.clear();
	}
	
	@Override
	public void readFromNBT(NBTTagCompound nbt)
	{
		super.readFromNBT(nbt);
		
		//load ship attributes
		CapaShipSavedValues.loadNBTData(nbt, this);
		
		//load ship inventory
        if (nbt.hasKey(CapaShipInventory.InvName))
        {
        	itemHandler.deserializeNBT((NBTTagCompound) nbt.getTag(CapaShipInventory.InvName));
        }
	}
	
	@Override
	public NBTTagCompound writeToNBT(NBTTagCompound nbt)
	{
		super.writeToNBT(nbt);
		
		//load ship attributes
		CapaShipSavedValues.saveNBTData(nbt, this);
		
		//load ship inventory
		nbt.setTag(CapaShipInventory.InvName, itemHandler.serializeNBT());
		
		return nbt;
	}
	
	@Override
	public ShipPathNavigate getShipNavigate()
	{
		return shipNavigator;
	}
	
	@Override
	public ShipMoveHelper getShipMoveHelper()
	{
		return shipMoveHelper;
	}
	
	/** 1:cannon only, 2:both, 3:aircraft only */
	abstract public int getEquipType();
	
	/** 0:small, 1:large, 2:mob small, 3:mob large*/
	public int getKaitaiType()
	{
		return 0;
	}
	
	@Override
	public int getLevel()
	{
		return StateMinor[ID.M.ShipLevel];
	}
	
	public byte getShipType()
	{
		return (byte)getStateMinor(ID.M.ShipType);
	}
	
	public short getShipClass()
	{
		return (short) getStateMinor(ID.M.ShipClass);
	}
	
	//ShipUID = ship UNIQUE ID
	public int getShipUID()
	{
		return getStateMinor(ID.M.ShipUID);
	}
	
	//PlayerUID = player UNIQUE ID (not UUID)
	@Override
	public int getPlayerUID()
	{
		return getStateMinor(ID.M.PlayerUID);
	}
	
	@Override
	public int getAmmoLight()
	{
		return this.StateMinor[ID.M.NumAmmoLight];
	}

	@Override
	public int getAmmoHeavy()
	{
		return this.StateMinor[ID.M.NumAmmoHeavy];
	}
	
	@Override
	public int getFaceTick()
	{
		return this.StateTimer[ID.T.FaceTime];
	}
	
	@Override
	public int getHeadTiltTick()
	{
		return this.StateTimer[ID.T.HeadTilt];
	}
	
	@Override
	public int getAttackTick()
	{
		return this.StateTimer[ID.T.AttackTime];
	}
	
	@Override
	public int getAttackTick2()
	{
		return this.StateTimer[ID.T.AttackTime2];
	}
	
	//last caress time
	public int getMoraleTick()
	{
		return this.StateTimer[ID.T.MoraleTime];
	}
	
	//emotes CD
	public int getEmotesTick()
	{
		return this.StateTimer[ID.T.EmoteDelay];
	}
	
	//last attack time
	public int getCombatTick()
	{
		return this.StateTimer[ID.T.LastCombat];
	}
	
	/** 被pointer item點到的高度, 以百分比值表示 */
	public int getHitHeight()
	{
		return this.StateMinor[ID.M.HitHeight];
	}
	
	/** 被pointer item點到的角度, 0~-360
	 *  front: -180
	 *  back: 0/-360
	 *  right:-90
	 *  left:-270
	 */
	public int getHitAngle()
	{
		return this.StateMinor[ID.M.HitAngle];
	}
	
	@Override
	public int getTickExisted()
	{
		return this.ticksExisted;
	}
	
	@Override
	public float getSwingTime(float partialTick)
	{
		return this.getSwingProgress(partialTick);
	}
	
	@Override
	public boolean getIsRiding()
	{
		return this.isRiding();
	}
	
	@Override
	public boolean getIsLeashed()
	{
		return this.getLeashed();
	}

	@Override
	public boolean getIsSprinting()
	{
		return this.isSprinting();
	}

	@Override
	public boolean getIsSitting()
	{
		return this.isSitting();
	}

	@Override
	public boolean getIsSneaking()
	{
		return this.isSneaking();
	}
	
	@Override
	public Entity getEntityTarget()
	{
		return this.atkTarget;
	}
	
	@Override
	public Entity getEntityRevengeTarget()
	{
		return this.rvgTarget;
	}

	@Override
	public int getEntityRevengeTime()
	{
		return this.StateTimer[ID.T.RevengeTime];
	}
	
	@Override
	public float getAttackDamage()
	{	//NO USE for ship entity
		return 0;
	}
	
	@Override
	public float getAttackSpeed()
	{
		return this.StateFinal[ID.SPD];
	}
	
	@Override
	public float getAttackRange()
	{
		return this.StateFinal[ID.HIT];
	}
	
	@Override
	public boolean getAttackType(int par1)
	{
		return this.getStateFlag(par1);
	}
	
	@Override
	public float getDefValue()
	{
		return this.StateFinal[ID.DEF];
	}
	
	@Override
	public float getMoveSpeed()
	{
		return this.StateFinal[ID.MOV];
	}
	
	@Override
    public float getAIMoveSpeed()
    {
        return getMoveSpeed();
    }
	
	@Override
	public boolean hasAmmoLight()
	{
		return StateMinor[ID.M.NumAmmoLight] >= StateMinor[ID.M.AmmoCon];
	}
	
	@Override
	public boolean hasAmmoHeavy()
	{
		return StateMinor[ID.M.NumAmmoHeavy] >= StateMinor[ID.M.AmmoCon];
	}

	@Override
	public boolean useAmmoLight()
	{
		return StateFlag[ID.F.UseAmmoLight];
	}

	@Override
	public boolean useAmmoHeavy()
	{
		return StateFlag[ID.F.UseAmmoHeavy];
	}
	
	@Override
	public double getShipDepth()
	{
		return ShipDepth;
	}
	
	@Override
	public boolean getStateFlag(int flag)
	{	//get flag (boolean)
		return StateFlag[flag];
	}
	
	public byte getStateFlagI(int flag)
	{	//get flag (byte)
		if (StateFlag[flag])
		{
			return 1;
		}
		else
		{
			return 0;
		}
	}
	
	public float getStateEquip(int id)
	{
		return StateEquip[id];
	}
	
	public float getStateFinal(int id)
	{
		return StateFinal[id];
	}
	
	public float getStateFinalBU(int id)
	{
		return StateFinalBU[id];
	}
	
	@Override
	public int getStateMinor(int id)
	{
		return StateMinor[id];
	}
	
	@Override
	public float getEffectEquip(int id)
	{
		return EffectEquip[id];
	}
	
	public float getEffectEquipBU(int id)
	{
		return EffectEquipBU[id];
	}
	
	public boolean getUpdateFlag(int id)
	{
		return UpdateFlag[id];
	}
	
	public float getEffectFormation(int id)
	{
		return EffectFormation[id];
	}
	
	public float getEffectFormationFixed(int id)
	{
		return EffectFormationFixed[id];
	}
	
	public int getStateTimer(int id)
	{
		return StateTimer[id];
	}
	
	@Override
	public byte getStateEmotion(int id)
	{
		return StateEmotion[id];
	}
	
	public byte getBonusPoint(int id)
	{
		return BonusPoint[id];
	}
	
	public float getTypeModify(int id)
	{
		return TypeModify[id];
	}
	
	/** get model position in GUI */
	public float[] getModelPos()
	{
		return ModelPos;
	}
	
	/** grudge consumption when IDLE */
	public int getGrudgeConsumption()
	{
		return getStateMinor(ID.M.GrudgeCon);
	}
	
	public int getAmmoConsumption()
	{
		return getStateMinor(ID.M.AmmoCon);
	}
	
	public int getFoodSaturation()
	{
		return getStateMinor(ID.M.Food);
	}
	
	public int getFoodSaturationMax()
	{
		return getStateMinor(ID.M.FoodMax);
	}
	
	public CapaShipInventory getCapaShipInventory()
	{
		return this.itemHandler;
	}
	
	/**calc equip, buff, debuff and all attrs
	 * 
	 * step:
	 * 1. reset all attrs to 0
	 * 2. calc 6 equip slots
	 * 3. calc special attrs (if @Override calcShipAttributes())
	 * 4. calc HP,DEF...etc
	 * 5. backup unbuff attrs
	 * 6. calc buffs
	 * 7. send sync packet
	 * 
	 */
	public void calcEquipAndUpdateState()
	{
		ItemStack itemstack = null;
		float[] equipStat = null;
		
		//init value
		StateEquip[ID.HP] = 0F;
		StateEquip[ID.DEF] = 0F;
		StateEquip[ID.SPD] = 0F;
		StateEquip[ID.MOV] = 0F;
		StateEquip[ID.HIT] = 0F;
		StateEquip[ID.ATK] = 0F;
		StateEquip[ID.ATK_H] = 0F;
		StateEquip[ID.ATK_AL] = 0F;
		StateEquip[ID.ATK_AH] = 0F;
		
		EffectEquip[ID.EF_CRI] = 0F;
		EffectEquip[ID.EF_DHIT] = 0F;
		EffectEquip[ID.EF_THIT] = 0F;
		EffectEquip[ID.EF_MISS] = 0F;
		EffectEquip[ID.EF_AA] = 0F;
		EffectEquip[ID.EF_ASM] = 0F;
		EffectEquip[ID.EF_DODGE] = 0F;
		
		StateMinor[ID.M.InvSize] = 0;
		StateMinor[ID.M.LevelChunkLoader] = 0;
		StateMinor[ID.M.LevelFlare] = 0;
		StateMinor[ID.M.LevelSearchlight] = 0;
		
		//calc equip attrs
		for (int i = 0; i < ContainerShipInventory.SLOTS_SHIPINV; i++)
		{
			//get normal stats
			equipStat = EquipCalc.getEquipStat(this, this.itemHandler.getStackInSlot(i));
			
			if (equipStat != null)
			{
				StateEquip[ID.HP] += equipStat[ID.E.HP];
				StateEquip[ID.DEF] += equipStat[ID.E.DEF];
				StateEquip[ID.SPD] += equipStat[ID.E.SPD];
				StateEquip[ID.MOV] += equipStat[ID.E.MOV];
				StateEquip[ID.HIT] += equipStat[ID.E.HIT];
				StateEquip[ID.ATK] += equipStat[ID.E.ATK_L];
				StateEquip[ID.ATK_H] += equipStat[ID.E.ATK_H];
				StateEquip[ID.ATK_AL] += equipStat[ID.E.ATK_AL];
				StateEquip[ID.ATK_AH] += equipStat[ID.E.ATK_AH];
				
				EffectEquip[ID.EF_CRI] += equipStat[ID.E.CRI];
				EffectEquip[ID.EF_DHIT] += equipStat[ID.E.DHIT];
				EffectEquip[ID.EF_THIT] += equipStat[ID.E.THIT];
				EffectEquip[ID.EF_MISS] += equipStat[ID.E.MISS];
				EffectEquip[ID.EF_AA] += equipStat[ID.E.AA];
				EffectEquip[ID.EF_ASM] += equipStat[ID.E.ASM];
				EffectEquip[ID.EF_DODGE] += equipStat[ID.E.DODGE];
			}
			
			//get special stats
			equipStat = EquipCalc.getEquipStatMisc(this, this.itemHandler.getStackInSlot(i));
			
			if (equipStat != null)
			{
				StateMinor[ID.M.InvSize] += equipStat[0];
				StateMinor[ID.M.LevelChunkLoader] += equipStat[1];
				StateMinor[ID.M.LevelFlare] += equipStat[2];
				StateMinor[ID.M.LevelSearchlight] += equipStat[3];
			}
		}
		
		//calc attrs
		calcShipAttributes();
	}
	
	/** calc ship attrs */
	public void calcShipAttributes()
	{
		//get attrs value
		float[] getStat = Values.ShipAttrMap.get(this.getShipClass());

		//HP = (base + equip + (point + 1) * level * typeModify) * config scale
		StateFinal[ID.HP] = (getStat[ID.ShipAttr.BaseHP] + StateEquip[ID.HP] + (float)(BonusPoint[ID.HP]+1F) * (float)StateMinor[ID.M.ShipLevel] * TypeModify[ID.HP]) * (float)ConfigHandler.scaleShip[ID.HP]; 
		//DEF = base + ((point + 1) * level / 3 * 0.4 + equip) * typeModify
		StateFinal[ID.DEF] = (getStat[ID.ShipAttr.BaseDEF] + StateEquip[ID.DEF] + ((float)(BonusPoint[ID.DEF]+1F) * ((float)StateMinor[ID.M.ShipLevel])/3F) * 0.4F * TypeModify[ID.DEF]) * (float)ConfigHandler.scaleShip[ID.DEF];
		//SPD = base + ((point + 1) * level / 10 * 0.02 + equip) * typeModify
		StateFinal[ID.SPD] = (getStat[ID.ShipAttr.BaseSPD] + StateEquip[ID.SPD] + ((float)(BonusPoint[ID.SPD]+1F) * ((float)StateMinor[ID.M.ShipLevel])/10F) * 0.04F * TypeModify[ID.SPD]) * (float)ConfigHandler.scaleShip[ID.SPD];
		//MOV = base + ((point + 1) * level / 10 * 0.01 + equip) * typeModify
		StateFinal[ID.MOV] = (getStat[ID.ShipAttr.BaseMOV] + StateEquip[ID.MOV] + ((float)(BonusPoint[ID.MOV]+1F) * ((float)StateMinor[ID.M.ShipLevel])/10F) * 0.02F * TypeModify[ID.MOV]) * (float)ConfigHandler.scaleShip[ID.MOV];
		//HIT = base + ((point + 1) * level / 10 * 0.3 + equip) * typeModify
		StateFinal[ID.HIT] = (getStat[ID.ShipAttr.BaseHIT] + StateEquip[ID.HIT] + ((float)(BonusPoint[ID.HIT]+1F) * ((float)StateMinor[ID.M.ShipLevel])/10F) * 0.2F * TypeModify[ID.HIT]) * (float)ConfigHandler.scaleShip[ID.HIT];
		//ATK = (base + equip + ((point + 1) * level / 3) * 0.5 * typeModify) * config scale
		float atk = getStat[ID.ShipAttr.BaseATK] + ((float)(BonusPoint[ID.ATK]+1F) * ((float)StateMinor[ID.M.ShipLevel])/3F) * 0.4F * TypeModify[ID.ATK];
		
		//add equip
		StateFinal[ID.ATK] = (atk + StateEquip[ID.ATK]) * (float)ConfigHandler.scaleShip[ID.ATK];
		StateFinal[ID.ATK_H] = (atk * 3F + StateEquip[ID.ATK_H]) * (float)ConfigHandler.scaleShip[ID.ATK];
		StateFinal[ID.ATK_AL] = (atk + StateEquip[ID.ATK_AL]) * (float)ConfigHandler.scaleShip[ID.ATK];
		StateFinal[ID.ATK_AH] = (atk * 3F + StateEquip[ID.ATK_AH]) * (float)ConfigHandler.scaleShip[ID.ATK];
		
		//apply morale buff before backup
		updateMoraleBuffs();
		
		//backup value before buffs, for GUI display
		this.StateFinalBU = this.StateFinal.clone();
		this.EffectEquipBU = this.EffectEquip.clone();
		
		//update formation buff
		updateFormationBuffs();
		
		//KB Resistance
		float resisKB = (float)StateMinor[ID.M.ShipLevel] * 0.005F;

		//check limit
		this.StateFinal = this.checkStateFinalLimit(this.StateFinal);
		this.StateFinalBU = this.checkStateFinalLimit(this.StateFinalBU);
		this.EffectEquip = this.checkEffectEquipLimit(EffectEquip);
		this.EffectEquipBU = this.checkEffectEquipLimit(EffectEquipBU);
		
		//check chunk loader
		updateChunkLoader();
		
		//set attribute by final value
		/**
		 * DO NOT SET ATTACK DAMAGE to non-EntityMob!!!!!
		 */
		getEntityAttribute(SharedMonsterAttributes.MAX_HEALTH).setBaseValue(StateFinal[ID.HP]);
		getEntityAttribute(SharedMonsterAttributes.MOVEMENT_SPEED).setBaseValue(StateFinal[ID.MOV]);
		getEntityAttribute(SharedMonsterAttributes.FOLLOW_RANGE).setBaseValue(40);
		getEntityAttribute(SharedMonsterAttributes.KNOCKBACK_RESISTANCE).setBaseValue(resisKB);
		
		//for server side, sync data to client
		if (!world.isRemote)
		{
			sendSyncPacketAllValue();
			sendSyncPacketUnbuffValue();
		}
	}
	
	//set next exp value (for client load nbt data, gui display)
	public void setExpNext()
	{
		StateMinor[ID.M.ExpNext] = StateMinor[ID.M.ShipLevel] * ConfigHandler.expMod + ConfigHandler.expMod;
	}
		
	//called when entity exp++
	public void addShipExp(int exp)
	{
		int capLevel = getStateFlag(ID.F.IsMarried) ? 150 : 100;
		
		//level is not cap level
		if (StateMinor[ID.M.ShipLevel] != capLevel && StateMinor[ID.M.ShipLevel] < 150)
		{
			StateMinor[ID.M.ExpCurrent] += exp;
			if(StateMinor[ID.M.ExpCurrent] >= StateMinor[ID.M.ExpNext]) {
				
				//level up sound
				if (this.rand.nextInt(4) == 0)
				{
					this.world.playSound(null, this.posX, this.posY, this.posZ, SoundEvents.ENTITY_PLAYER_LEVELUP, this.getSoundCategory(), 0.75F, 1F);
				}
				else
				{
					this.playSound(ModSounds.SHIP_LEVEL, 0.75F, 1F);
				}
				
				StateMinor[ID.M.ExpCurrent] -= StateMinor[ID.M.ExpNext];	//level up
				StateMinor[ID.M.ExpNext] = (StateMinor[ID.M.ShipLevel] + 2) * ConfigHandler.expMod;
				setShipLevel(++StateMinor[ID.M.ShipLevel], true);
			}
		}	
	}
	
	@Override
	public void setShipDepth(double par1)
	{
		this.ShipDepth = par1;
	}
	
	//called when entity level up
	public void setShipLevel(int par1, boolean update)
	{
		//set level
		if (par1 < 151)
		{
			StateMinor[ID.M.ShipLevel] = par1;
		}
		//update attributes
		if (update)
		{
			LogHelper.info("DEBUG : set ship level with update");
			calcEquipAndUpdateState();
			this.setHealth(this.getMaxHealth());
		}
	}
	
	//prevent player use name tag
	@Override
	public void setCustomNameTag(String str) {}
	
	//custom name tag method
	public void setNameTag(String str)
	{
		super.setCustomNameTag(str);
    }
	
	//called when a mob die near the entity (used in event handler)
	public void addKills()
	{
		StateMinor[ID.M.Kills]++;
	}
	
	@Override
	public void setAmmoLight(int num)
	{
		this.StateMinor[ID.M.NumAmmoLight] = num;
	}
	
	@Override
	public void setAmmoHeavy(int num)
	{
		this.StateMinor[ID.M.NumAmmoHeavy] = num;
	}
	
	public void setStateFinal(int id, float par1)
	{
		StateFinal[id] = par1;
	}
	
	public void setStateFinalBU(int id, float par1)
	{
		StateFinalBU[id] = par1;
	}
	
	@Override
	public void setStateMinor(int id, int par1)
	{
		//value limit
		switch (id)
		{
		case ID.M.Morale:
			if (par1 < 0) par1 = 0;
			break;
		case ID.M.CraneState:
			//if changed to 1 or 2, check delay
			if (par1 > 0)
			{
				if (getStateTimer(ID.T.CrandDelay) > 0)
				{
					return;
				}
				else
				{
					setStateTimer(ID.T.CrandDelay, 40);
				}
			}
			break;
		}
		
		//set value
		StateMinor[id] = par1;
	}
	
	public void setUpdateFlag(int id, boolean par1)
	{
		UpdateFlag[id] = par1;
	}
	
	public void setEffectEquip(int id, float par1)
	{
		EffectEquip[id] = par1;
	}
	
	public void setEffectEquipBU(int id, float par1)
	{
		EffectEquipBU[id] = par1;
	}
	
	public void setEffectFormation(int id, float par1)
	{
		EffectFormation[id] = par1;
	}
	
	public void setEffectFormation(float[] par1)
	{
		EffectFormation = par1;
	}
	
	public void setEffectFormationFixed(int id, float par1)
	{
		EffectFormationFixed[id] = par1;
	}
	
	public void setBonusPoint(int id, byte par1)
	{
		BonusPoint[id] = par1;
	}
	
	@Override
	public void setEntitySit(boolean sit)
	{
		this.setSitting(sit);
		
		//若設定為坐下, 則清空路徑跟攻擊目標
		if (sit)
		{
	        this.isJumping = false;
	        this.getShipNavigate().clearPathEntity();
	        this.getNavigator().clearPathEntity();
	        this.setAttackTarget(null);
	        this.setEntityTarget(null);
		}
	}
	
	public void setRiderAndMountSit()
	{
		//loop all mount to set sitting
		Entity mount = this.getRidingEntity();
		while (mount instanceof BasicEntityShip)
		{
			((BasicEntityShip) mount).setEntitySit(this.isSitting());
			mount = mount.getRidingEntity();
		}
        
		//set all rider sitting
		List<Entity> rider = this.getPassengers();
		for (Entity r : rider)
		{
			if (r instanceof BasicEntityShip)
			{
				((BasicEntityShip) r).setEntitySit(this.isSitting());
			}
		}
	}

	//called when load nbt data or GUI click
	@Override
	public void setStateFlag(int id, boolean par1)
	{
		this.StateFlag[id] = par1;
		
		//若修改melee flag, 則reload AI
		if (!this.world.isRemote)
		{ 
			if (id == ID.F.UseMelee)
			{
				clearAITasks();
	    		setAIList();
	    		
	    		//設定mount的AI
				if (this.getRidingEntity() instanceof BasicEntityMount)
				{
					((BasicEntityMount) this.getRidingEntity()).clearAITasks();
					((BasicEntityMount) this.getRidingEntity()).setAIList();
				}
			}
			else if (id == ID.F.PassiveAI)
			{
				clearAITargetTasks();
				setAITargetList();
			}
		}
	}
	
	//called when load nbt data or GUI click
	public void setStateFlagI(int id, int par1)
	{
		if (par1 > 0)
		{
			setStateFlag(id, true);
		}
		else
		{
			setStateFlag(id, false);
		}
	}
	
	//called when entity spawn, set the type modify
	public void initTypeModify()
	{
		//get attrs value
		float[] getStat = Values.ShipAttrMap.get(this.getShipClass());

		TypeModify[ID.HP] = getStat[ID.ShipAttr.ModHP];
		TypeModify[ID.ATK] = getStat[ID.ShipAttr.ModATK];
		TypeModify[ID.DEF] = getStat[ID.ShipAttr.ModDEF];
		TypeModify[ID.SPD] = getStat[ID.ShipAttr.ModSPD];
		TypeModify[ID.MOV] = getStat[ID.ShipAttr.ModMOV];
		TypeModify[ID.HIT] = getStat[ID.ShipAttr.ModHIT];
	}

	public void setStateTimer(int id, int value)
	{
		StateTimer[id] = value;
	}
	
	@Override
	public void setStateEmotion(int id, int value, boolean sync)
	{
		StateEmotion[id] = (byte)value;
		
		if (sync)
		{
			this.sendSyncPacketEmotion();
		}
	}
	
	//emotion start time (CLIENT ONLY), called from model class
	@Override
	public void setFaceTick(int par1)
	{
		this.StateTimer[ID.T.FaceTime] = par1;
	}
	
	@Override
	public void setHeadTiltTick(int par1)
	{
		this.StateTimer[ID.T.HeadTilt] = par1;
	}
	
	@Override
	public void setAttackTick(int par1)
	{
		this.StateTimer[ID.T.AttackTime] = par1;
	}
	
	@Override
	public void setAttackTick2(int par1)
	{
		this.StateTimer[ID.T.AttackTime2] = par1;
	}
	
	//last caress time
	public void setMoraleTick(int par1)
	{
		this.StateTimer[ID.T.MoraleTime] = par1;
	}
	
	//emotes CD
	public void setEmotesTick(int par1)
	{
		this.StateTimer[ID.T.EmoteDelay] = par1;
	}
	
	//last attack time
	public void setCombatTick(int par1)
	{
		this.StateTimer[ID.T.LastCombat] = par1;
	}
	
	/** 被pointer item點到的高度, 以百分比值表示 */
	public void setHitHeight(int par1)
	{
		this.StateMinor[ID.M.HitHeight] = par1;
	}
	
	/** 被pointer item點到的角度, 0~-360
	 *  front: -180
	 *  back: 0/-360
	 *  right:-90
	 *  left:-270
	 */
	public void setHitAngle(int par1)
	{
		this.StateMinor[ID.M.HitAngle] = par1;
	}
	
	public void setShipUID(int par1)
	{
		this.setStateMinor(ID.M.ShipUID, par1);
	}
	
	@Override
	public void setPlayerUID(int par1)
	{
		this.setStateMinor(ID.M.PlayerUID, par1);
	}
	

  	@Override
	public void setEntityTarget(Entity target)
  	{
		this.atkTarget = target;
	}
  	
  	@Override
	public void setEntityRevengeTarget(Entity target)
  	{
		this.rvgTarget = target;
	}
  	
  	@Override
	public void setEntityRevengeTime()
  	{
  		this.StateTimer[ID.T.RevengeTime] = this.ticksExisted;
	}
  	
  	public void setGrudgeConsumption(int par1)
  	{
  		if (par1 > 120) par1 = 120;
  		this.setStateMinor(ID.M.GrudgeCon, par1);
  	}
  	
  	public void setAmmoConsumption(int par1)
  	{
  		if (par1 > 45) par1 = 45;
  		this.setStateMinor(ID.M.AmmoCon, par1);
  	}
  	
  	public void setFoodSaturation(int par1)
  	{
  		setStateMinor(ID.M.Food, par1);
	}
	
	public void setFoodSaturationMax(int par1)
	{
		setStateMinor(ID.M.FoodMax, par1);
	}
	
	/** send sync packet: sync all data */
	public void sendSyncPacketAllValue()
	{
		sendSyncPacket(S2CEntitySync.PID.SyncShip_All, false);
	}
	
	/** send sync packet: sync unbuff data */
	public void sendSyncPacketUnbuffValue()
	{
		sendSyncPacket(S2CEntitySync.PID.SyncShip_Unbuff, false);
	}
	
	/** send sync packet: sync formation data */
	public void sendSyncPacketFormationValue()
	{
		sendSyncPacket(S2CEntitySync.PID.SyncShip_Formation, false);
	}
	
	/**  sync data for GUI display */
	public void sendGUISyncPacket()
	{
		if (!this.world.isRemote)
		{
			if (this.getPlayerUID() > 0)
			{
				EntityPlayerMP player = (EntityPlayerMP) EntityHelper.getEntityPlayerByUID(this.getPlayerUID());
				
				//owner在附近才需要sync
				if (player != null && player.dimension == this.dimension &&
					this.getDistanceToEntity(player) < 32F)
				{
					CommonProxy.channelG.sendTo(new S2CGUIPackets(this), player);
				}
			}
		}
	}
	
	/** sync data for timer display */
	public void sendSyncPacketTimer()
	{
		sendSyncPacket(S2CEntitySync.PID.SyncShip_Timer, true);
	}
	
	/** sync data for emotion display */
	public void sendSyncPacketEmotion()
	{
		sendSyncPacket(S2CEntitySync.PID.SyncShip_Emo, true);
	}
	
	/** sync data for flag */
	public void sendSyncPacketFlag()
	{
		sendSyncPacket(S2CEntitySync.PID.SyncShip_Flag, true);
	}
	
	/** send sync packet:
	 *  type: 0: all  1: emotion  2: flag  3: minor
	 *  send all: send packet to all around player
	 *  sync emo: sync emotion to all around player
	 */
	public void sendSyncPacket(byte type, boolean sendAll)
	{
		if (!world.isRemote)
		{
			//send to all player
			if (sendAll)
			{
				TargetPoint point = new TargetPoint(this.dimension, this.posX, this.posY, this.posZ, 32D);
				CommonProxy.channelE.sendToAllAround(new S2CEntitySync(this, type), point);
			}
			else
			{
				EntityPlayerMP player = null;
				
				//for owner, send all data
				if (this.getPlayerUID() > 0)
				{
					player = (EntityPlayerMP) EntityHelper.getEntityPlayerByUID(this.getPlayerUID());
				}
				
				//owner在附近才需要sync
				if (player != null && this.getDistanceToEntity(player) <= 48F)
				{
					CommonProxy.channelE.sendTo(new S2CEntitySync(this, type), player);
				}
			}
		}
	}
	
	/**
	 * 1.9.4:
	 * EnumActionResult:
	 *   PASS:本方法的動作成功, 並且繼續給其他interact方法判定
	 *   FAIL:本方法的動作失敗, 並且禁止其他interact
	 *   SUCCESS:本方法的動作成功, 並且禁止其他interact
	 */
    public EnumActionResult applyPlayerInteraction(EntityPlayer player, Vec3d vec, @Nullable ItemStack stack, EnumHand hand)
    {
    	if (hand == EnumHand.OFF_HAND) return EnumActionResult.FAIL;
    	
		//use item
		if (stack != null)
		{
			//use pointer item (caress head mode)
			if (stack.getItem() == ModItems.PointerItem && !this.world.isRemote)
			{
				//set ai target
				this.aiTarget = player;
				
				//is owner
				if (TeamHelper.checkSameOwner(player, this) && !this.getStateFlag(ID.F.NoFuel))
				{
					int t = this.ticksExisted - this.getMoraleTick();
					int m = this.getStateMinor(ID.M.Morale);
					
					if (t > 3 && m < 6600)
					{  //if caress > 3 ticks
						this.setMoraleTick(this.ticksExisted);
						this.setStateMinor(ID.M.Morale, m + ConfigHandler.baseCaressMorale);
					}

					//show emotes
					applyEmotesReaction(0);
				}
				//not owner or no fuel
				else
				{
					applyEmotesReaction(1);
				}
				
				//clear ai target
				this.aiTarget = null;
				
				return EnumActionResult.SUCCESS;
			}
			
			//use name tag, owner only
			if (stack.getItem() == Items.NAME_TAG && TeamHelper.checkSameOwner(player, this))
			{
	            //若該name tag有取名過, 則將名字貼到entity上
				if (stack.hasDisplayName())
				{
					this.setNameTag(stack.getDisplayName());
					return EnumActionResult.FAIL;  //TODO 回傳FAIL可避免tag被消耗掉?
	            } 
			}
			
			//use repair bucket
			if (stack.getItem() == ModItems.BucketRepair)
			{
				//hp不到max hp時可以使用bucket
				if (this.getHealth() < this.getMaxHealth())
				{
					if (!player.capabilities.isCreativeMode)
					{  //item-1 in non-creative mode
						--stack.stackSize;
	                }
	
	                if (this instanceof BasicEntityShipSmall)
	                {
	                	this.heal(this.getMaxHealth() * 0.1F + 5F);	//1 bucket = 10% hp for small ship
	                }
	                else
	                {
	                	this.heal(this.getMaxHealth() * 0.05F + 10F);	//1 bucket = 5% hp for large ship
	                }
	                
	                //airplane++
	                if (this instanceof BasicEntityShipCV)
	                {
	                	BasicEntityShipCV ship = (BasicEntityShipCV) this;
	                	ship.setNumAircraftLight(ship.getNumAircraftLight() + 1);
	                	ship.setNumAircraftHeavy(ship.getNumAircraftHeavy() + 1);
	                }
	                
	                if (stack.stackSize <= 0)
	                {  
	                	player.inventory.setInventorySlotContents(player.inventory.currentItem, (ItemStack)null);
	                }
	                
	                return EnumActionResult.SUCCESS;
	            }			
			}
			
			//use kaitai hammer, OWNER and OP only
			if (stack.getItem() == ModItems.KaitaiHammer && player.isSneaking() &&
				(TeamHelper.checkSameOwner(this, player) || EntityHelper.checkOP(player)))
			{
				
				//client
				if (world.isRemote)
				{
					return EnumActionResult.SUCCESS;
				}
				//server
				else
				{
					//創造模式不消耗物品
	                if (!player.capabilities.isCreativeMode)
	                {	//damage +1 in non-creative mode
	 	                stack.setItemDamage(stack.getItemDamage() + 1);
	                    
	                    //set item amount
	                    ItemStack[] items = ShipCalc.getKaitaiItems(this.getShipClass());
	                                
	                    EntityItem entityItem0 = new EntityItem(world, posX+0.5D, posY+0.8D, posZ+0.5D, items[0]);
	                    EntityItem entityItem1 = new EntityItem(world, posX+0.5D, posY+0.8D, posZ-0.5D, items[1]);
	                    EntityItem entityItem2 = new EntityItem(world, posX-0.5D, posY+0.8D, posZ+0.5D, items[2]);
	                    EntityItem entityItem3 = new EntityItem(world, posX-0.5D, posY+0.8D, posZ-0.5D, items[3]);

	                    world.spawnEntity(entityItem0);
	                    world.spawnEntity(entityItem1);
	                    world.spawnEntity(entityItem2);
	                    world.spawnEntity(entityItem3);
	                    
	                    //drop inventory item
                    	for (int i = 0; i < this.itemHandler.getSlots(); i++)
                    	{
            				ItemStack invitem = this.itemHandler.getStackInSlot(i);

            				if (invitem != null)
            				{
            					//設定要隨機噴出的range
            					float f = rand.nextFloat() * 0.8F + 0.1F;
            					float f1 = rand.nextFloat() * 0.8F + 0.1F;
            					float f2 = rand.nextFloat() * 0.8F + 0.1F;

            					while (invitem.stackSize > 0)
            					{
            						int j = rand.nextInt(21) + 10;
            						//如果物品超過一個隨機數量, 會分更多疊噴出
            						if (j > invitem.stackSize)
            						{  
            							j = invitem.stackSize;
            						}

            						invitem.stackSize -= j;
            						
            						//將item做成entity, 生成到世界上
            						EntityItem item = new EntityItem(this.world, this.posX+f, this.posY+f1, this.posZ+f2, new ItemStack(invitem.getItem(), j, invitem.getItemDamage()));
            						
            						//如果有NBT tag, 也要複製到物品上
            						if (invitem.hasTagCompound())
            						{
            							item.getEntityItem().setTagCompound((NBTTagCompound)invitem.getTagCompound().copy());
            						}
            						
            						world.spawnEntity(item);	//生成item entity
            					}
            				}
            			}
	                    
                    	//kaitai sound
                    	this.playSound(ModSounds.SHIP_KAITAI, this.getSoundVolume(), this.getSoundPitch());
                    	this.playSound(this.getDeathSound(), this.getSoundVolume(), this.getSoundPitch());
	                }
	                
	                //物品用完時要設定為null清空該slot
	                if (stack.getItemDamage() >= stack.getMaxDamage())
	                {  //物品耐久度用完時要設定為null清空該slot
	                	player.inventory.setInventorySlotContents(player.inventory.currentItem, (ItemStack)null);
	                }
	                
	                //show emotes
					applyParticleEmotion(8);
					
					//emotes AOE
					EntityHelper.applyShipEmotesAOE(this.world, this.posX, this.posY, this.posZ, 10D, 6);
	                 
	                this.setDead();
	                
	                return EnumActionResult.SUCCESS;
				}//end server side
			}//end kaitai hammer
			
			//use marriage ring
			if (stack.getItem() == ModItems.MarriageRing && !this.getStateFlag(ID.F.IsMarried) && 
			   player.isSneaking() && TeamHelper.checkSameOwner(this, player))
			{
				//stack-1 in non-creative mode
				if (!player.capabilities.isCreativeMode)
				{
                    --stack.stackSize;
                }

				//set marriage flag
                this.setStateFlag(ID.F.IsMarried, true);
                
                //player marriage num +1
    			CapaTeitoku capa = CapaTeitoku.getTeitokuCapability(player);
    			if (capa != null)
    			{
    				capa.setMarriageNum(capa.getMarriageNum() + 1);
    			}
                
                //play hearts effect
                TargetPoint point = new TargetPoint(this.dimension, this.posX, this.posY, this.posZ, 32D);
    			CommonProxy.channelP.sendToAllAround(new S2CSpawnParticle(this, 3, false), point);
                
    			//play marriage sound
    			this.playSound(this.getCustomSound(4, this), this.getSoundVolume(), 1F);
    	        
    	        //add 3 random bonus point
    	        for (int i = 0; i < 3; ++i)
    	        {
    	        	addRandomBonusPoint();
    	        }
    	        
    	        this.calcEquipAndUpdateState();
    	        
                if (stack.stackSize <= 0)
                {  //物品用完時要設定為null清空該slot
                	player.inventory.setInventorySlotContents(player.inventory.currentItem, (ItemStack)null);
                }
                
                return EnumActionResult.SUCCESS;
			}//end wedding ring
			
			//use modernization kit
			if (stack.getItem() == ModItems.ModernKit)
			{
				if (addRandomBonusPoint())
				{	//add 1 random bonus
					if (!player.capabilities.isCreativeMode)
					{
	                    --stack.stackSize;
	                    
	                    if (stack.stackSize <= 0)
	                    {  //物品用完時要設定為null清空該slot
	                    	player.inventory.setInventorySlotContents(player.inventory.currentItem, (ItemStack)null);
	                    }
	                }
					
					//play marriage sound
					this.playSound(this.getCustomSound(4, this), this.getSoundVolume(), 1F);
					
					return EnumActionResult.SUCCESS;
				}	
			}//end modern kit

			//use owner paper, owner only
			if (stack.getItem() == ModItems.OwnerPaper && TeamHelper.checkSameOwner(this, player))
			{
				if(interactOwnerPaper(player, stack)) return EnumActionResult.SUCCESS;
			}//end owner paper
			
			//use lead
			if (stack.getItem() == Items.LEAD && !this.getLeashed() && TeamHelper.checkSameOwner(this, player))
			{
				this.getShipNavigate().clearPathEntity();
				this.setLeashedToEntity(player, true);
				return EnumActionResult.SUCCESS;
	        }//end lead
			
			//feed
			if (!this.world.isRemote && interactFeed(player, stack))
			{
				return EnumActionResult.SUCCESS;
			}
			
		}//end item != null
		
		//如果已經被綑綁, 再點一下可以解除綑綁
		if (this.getLeashed() && this.getLeashedToEntity() == player)
		{
            this.clearLeashed(true, !player.capabilities.isCreativeMode);
            return EnumActionResult.SUCCESS;
        }
	
		//right click
		if (!this.world.isRemote && TeamHelper.checkSameOwner(this, player))
		{
			//sneak: open GUI
			if (player.isSneaking())
			{
				FMLNetworkHandler.openGui(player, ShinColle.instance, ID.Gui.SHIPINVENTORY, this.world, this.getEntityId(), 0, 0);
	    		return EnumActionResult.SUCCESS;
			}
			else
			{
				//current item = pointer
				if(stack != null && stack.getItem() == ModItems.PointerItem)
				{
					//call sitting method by PointerItem class, not here
				}
				else
				{
					this.setEntitySit(!this.isSitting());
					setRiderAndMountSit();
				}
				
				return EnumActionResult.SUCCESS;
			}
		}
		
		return EnumActionResult.PASS;
    }
	
	/** change owner method:
	 *  1. check owner paper has 2 signs
	 *  2. check owner is A or B
	 *  3. get player entity
	 *  4. change ship's player UID
	 *  5. change ship's owner UUID
	 */
	protected boolean interactOwnerPaper(EntityPlayer player, ItemStack itemstack)
	{
		NBTTagCompound nbt = itemstack.getTagCompound();
		boolean changeOwner = false;
		
		if (nbt != null)
		{
			int ida = nbt.getInteger(OwnerPaper.SignIDA);
			int idb = nbt.getInteger(OwnerPaper.SignIDB);
			int idtarget = -1;	//target player uid
			
			//1. check 2 signs
			if (ida > 0 && idb > 0)
			{
				//2. check owner is A or B
				if (ida == this.getPlayerUID())
				{	//A is owner
					idtarget = idb;
				}
				else
				{	//B is owner
					idtarget = ida;
				}
				
				//3. check player online
				EntityPlayer target = EntityHelper.getEntityPlayerByUID(idtarget);
				CapaTeitoku capa = CapaTeitoku.getTeitokuCapability(target);
				
				if (capa != null)
				{
					//4. change ship's player UID
					this.setPlayerUID(idtarget);
					
					//5. change ship's owner UUID
					this.setOwnerId(target.getUniqueID());
					
					LogHelper.info("DEBUG : change owner: from: pid "+this.getPlayerUID()+" uuid "+this.getOwner().getUniqueID());
					LogHelper.info("DEBUG : change owner: to: pid "+idtarget+" uuid "+target.getUniqueID().toString());
					changeOwner = true;
					
					//send sync packet
					this.sendSyncPacket(S2CEntitySync.PID.SyncShip_ID, true);
				}//end target != null
			}//end item has 2 signs
		}//end item has nbt
		
		if (changeOwner)
		{
			//play marriage sound
			this.playSound(this.getCustomSound(4, this), this.getSoundVolume(), 1F);
	        
			player.inventory.setInventorySlotContents(player.inventory.currentItem, (ItemStack)null);
			return true;
		}
		
		return false;
	}
	
	/** feed
	 * 
	 *  1. morale++
	 *  2. show emotion
	 *  3. sometimes reject food
	 *  4. feed max morale = 4800
	 */
	protected boolean interactFeed(EntityPlayer player, ItemStack itemstack)
	{
		Item i = itemstack.getItem();
		int meta = itemstack.getItemDamage();
		int type = 0;
		int mvalue = this.getStateMinor(ID.M.Morale);
		int mfood = 1;
		int addgrudge = 0;
		int addammo = 0;
		int addammoh = 0;
		int addsatur = 0;
		
		//max 4800 or max saturation, reject food
		if ((i instanceof ItemFood || i instanceof IShipFoodItem) &&
			getFoodSaturation() >= getFoodSaturationMax())
		{
			if (this.getEmotesTick() <= 0)
			{
				this.setEmotesTick(40);
				switch (this.rand.nextInt(4))
				{
				case 1:
					applyParticleEmotion(2);  //panic
					break;
				case 2:
					applyParticleEmotion(32);  //hmm
					break;
				case 3:
					applyParticleEmotion(0);  //drop
					break;
				default:
					applyParticleEmotion(11);  //??
					break;
				}
			}
			
			return false;
		}

		if (i instanceof ItemFood)
		{
			type = 1;
			ItemFood f = (ItemFood) i;
			float fv = f.getHealAmount(itemstack);			//food value
			float sv = f.getSaturationModifier(itemstack);  //saturation value
			if(fv < 1F) fv = 1F;
			mfood = (int)((fv + this.rand.nextInt((int)fv + 5)) * sv * 20F);
			addgrudge = mfood;
		}//end itemfood
		else if (i instanceof IShipFoodItem)
		{
			type = 2;
			int foodv = (int) ((IShipFoodItem) i).getFoodValue(meta);
			mfood = foodv + this.rand.nextInt(foodv + 1);
			
			switch (((IShipFoodItem) i).getSpecialEffect(meta))
			{
			case 1:  //grudge
				addgrudge = 300 + this.rand.nextInt(500);
				break;
			case 2:  //abyssium
				heal(getMaxHealth() * 0.05F + 1F);
				break;
			case 3:  //ammo
				switch (meta)
				{
				case 0:
					addammo = 30 + this.rand.nextInt(10);
					break;
				case 1:
					addammo = 270 + this.rand.nextInt(90);
					break;
				case 2:
					addammoh = 15 + this.rand.nextInt(5);
					break;
				case 3:
					addammoh = 135 + this.rand.nextInt(45);
					break;
				}
				break;
			case 4:  //polymetal
				//add airplane if CV
				if (this instanceof BasicEntityShipCV && this.rand.nextInt(10) > 4)
				{
					((BasicEntityShipCV)this).setNumAircraftLight(((BasicEntityShipCV)this).getNumAircraftLight()+1);
					((BasicEntityShipCV)this).setNumAircraftHeavy(((BasicEntityShipCV)this).getNumAircraftHeavy()+1);
				}
				break;
			case 5:  //toy plane
				//add airplane if CV
				if (this instanceof BasicEntityShipCV)
				{
					((BasicEntityShipCV)this).setNumAircraftLight(((BasicEntityShipCV)this).getNumAircraftLight()+rand.nextInt(3)+1);
					((BasicEntityShipCV)this).setNumAircraftHeavy(((BasicEntityShipCV)this).getNumAircraftHeavy()+rand.nextInt(3)+1);
				}
				break;
			case 6:  //combat ration
				addsatur = 7;
				addgrudge = mfood;
				mfood = 0;
				
				//add morale to happy (3900 ~ 5100)
				mfood = ((IShipCombatRation) i).getMoraleValue(meta);
				
				if (mvalue + mfood > 5000)
				{
					mfood = 0;
					mvalue = 5000;
				}
				
				break;
			}
		}//end ship food item
		
		//can feed
		if (type > 0)
		{
			//play sound
			if (this.StateTimer[ID.T.SoundTime] <= 0)
	    	{
	    		this.StateTimer[ID.T.SoundTime] = 20 + this.getRNG().nextInt(20);
	    		this.playSound(this.getCustomSound(7, this), this.getSoundVolume(), this.getSoundPitch());
	    	}
			
			//item--
			if (player != null)
			{
				if (!player.capabilities.isCreativeMode)
				{
		            if (--itemstack.stackSize <= 0)
		            {
		            	//update slot
		            	itemstack = itemstack.getItem().getContainerItem(itemstack);
		            	player.inventory.setInventorySlotContents(player.inventory.currentItem, itemstack);
		            }
		        }
			}
			
			//morale++
			this.setStateMinor(ID.M.Morale, mvalue + mfood);
			
			//saturation++
			this.setFoodSaturation(this.getFoodSaturation() + 1 + addsatur);
			
			//misc++
			StateMinor[ID.M.NumGrudge] += addgrudge;
			StateMinor[ID.M.NumAmmoLight] += addammo;
			StateMinor[ID.M.NumAmmoHeavy] += addammoh;

			//show eat emotion
			if (this.getEmotesTick() <= 0)
			{
				this.setEmotesTick(40);
				
				switch (this.rand.nextInt(3))
				{
				case 1:
					applyParticleEmotion(9);  //hungry
					break;
				case 2:
					applyParticleEmotion(30); //pif
					break;
				default:
					applyParticleEmotion(1);  //heart
					break;
				}
			}
			
			return true;
		}
		
		return false;
	}
	
	//add random bonus point, NO SYNC, server only!
	private boolean addRandomBonusPoint()
	{
		int bonusChoose = rand.nextInt(6);
		
		//bonus point +1 if bonus point < 3
		if (BonusPoint[bonusChoose] < 3)
		{
			BonusPoint[bonusChoose] = (byte) (BonusPoint[bonusChoose] + 1);
			return true;
		}
		else
		{	//select other bonus point
			for (int i = 0; i < BonusPoint.length; ++i)
			{
				if (BonusPoint[i] < 3)
				{
					BonusPoint[i] = (byte) (BonusPoint[i] + 1);
					return true;
				}
			}
		}
		
		return false;
	}

	/**修改移動方法, 使其water跟lava中移動時像是flying entity
     * Moves the entity based on the specified heading.  Args: strafe, forward
     */
	@Override
    public void moveEntityWithHeading(float strafe, float forward)
	{
		EntityHelper.moveEntityWithHeading(this, strafe, forward);
    }

	/** update entity 
	 *  在此用onUpdate跟onLivingUpdate區分server跟client update
	 *  for shincolle:
	 *  onUpdate = client update only
	 *  onLivingUpdate = server update only
	 */
	@Override
	public void onUpdate()
	{
		/** BOTH SIDE */
		if (stopAI)
		{
			return;
		}
		
		super.onUpdate();

		//get depth if in fluid block
		EntityHelper.checkDepth(this);
		
		//update arm
		updateArmSwingProgress();
		
		//update client timer
		updateBothSideTimer();
		
		//client side
		if (this.world.isRemote)
		{
			//有移動時, 產生水花特效
			if (this.getShipDepth() > 0D && !isRiding())
			{
				//(注意此entity因為設為非高速更新, client端不會更新motionX等數值, 需自行計算)
				double motX = this.posX - this.prevPosX;
				double motZ = this.posZ - this.prevPosZ;
				double parH = this.posY - (int)this.posY;
				double limit = 0.25D;
				
				if (motX > limit) motX = limit;
				else if (motX < -limit) motX = -limit;
				
				if (motZ > limit) motZ = limit;
				else if (motZ < -limit) motZ = -limit;
				
				
				if (motX != 0 || motZ != 0)
				{
					ParticleHelper.spawnAttackParticleAt(this.posX + motX*1.5D, this.posY + 0.4D, this.posZ + motZ*1.5D, 
							-motX*0.5D, 0D, -motZ*0.5D, (byte)15);
				}
			}
			
			//request model display sync after construction
			if (this.ticksExisted == 40)
			{
				CommonProxy.channelG.sendToServer(new C2SInputPackets(C2SInputPackets.PID.RequestSync_Model, this.getEntityId(), this.world.provider.getDimension()));
			}
			
			//check every 2 ticks
			if (this.ticksExisted % 2 == 0)
			{
				//faster body rotateYaw update (vanilla = 12~20 ticks?)
				updateClientBodyRotate();
			
				//check every 8 ticks
				if (this.ticksExisted % 8 == 0)
				{
	        		//update searchlight, CLIENT SIDE ONLY, NO block light value sync!
	        		if (ConfigHandler.canSearchlight)
	        		{
	            		updateSearchlight();
	        		}
	        		
	        		//check every 16 ticks
					if (this.ticksExisted % 16 == 0)
					{
						//generate HP state effect, use parm:lookX to send width size
						switch (getStateEmotion(ID.S.HPState))
						{
						case ID.HPState.MINOR:
							ParticleHelper.spawnAttackParticleAt(this.posX, this.posY + 0.7D, this.posZ, 
									this.width, 0.05D, 0D, (byte)4);
							break;
						case ID.HPState.MODERATE:
							ParticleHelper.spawnAttackParticleAt(this.posX, this.posY + 0.7D, this.posZ, 
									this.width, 0.05D, 0D, (byte)5);
							break;
						case ID.HPState.HEAVY:
							ParticleHelper.spawnAttackParticleAt(this.posX, this.posY + 0.7D, this.posZ, 
									this.width, 0.05D, 0D, (byte)7);
							break;
						default:
							break;
						}
						
						//check every 32 ticks
						if (this.ticksExisted % 32 == 0)
						{
							//show guard position
							if (!this.getStateFlag(ID.F.CanFollow))
							{
								//set guard entity
								if (this.getStateMinor(ID.M.GuardID) > 0)
								{
									Entity getEnt = EntityHelper.getEntityByID(this.getStateMinor(ID.M.GuardID), 0, true);
									this.setGuardedEntity(getEnt);
								}
								else
								{
									//reset guard entity
									this.setGuardedEntity(null);
								}
							}//end show pointer target effect
							
							//display circle particle, 只有owner才會接收到該ship同步的EID, 非owner讀取到的EID <= 0
							//get owner entity
							EntityPlayer player = null;
							if (this.getStateMinor(ID.M.PlayerEID) > 0)
							{
								player = EntityHelper.getEntityPlayerByID(getStateMinor(ID.M.PlayerEID), 0, true);
							}
							
							//show circle particle on ship and guard target
							if (player != null && player.dimension == this.getGuardedPos(3))
							{
								ItemStack item = player.inventory.getCurrentItem();
								
								if (ConfigHandler.alwaysShowTeamParticle ||
									(item != null && item.getItem() instanceof PointerItem &&
									 item.getItemDamage() < 3))
								{
									//show friendly particle
									ParticleHelper.spawnAttackParticleAtEntity(this, 0.3D, 7D, 0D, (byte)2);
									
									//show guard particle
									//標記在entity上
									if (this.getGuardedEntity() != null)
									{
										ParticleHelper.spawnAttackParticleAtEntity(this.getGuardedEntity(), 0.3D, 6D, 0D, (byte)2);
										ParticleHelper.spawnAttackParticleAtEntity(this, this.getGuardedEntity(), 0D, 0D, 0D, (byte)3, false);
									}
									//標記在block上
									else if (this.getGuardedPos(1) >= 0)
									{
										ParticleHelper.spawnAttackParticleAt(this.getGuardedPos(0)+0.5D, this.getGuardedPos(1), this.getGuardedPos(2)+0.5D, 0.3D, 6D, 0D, (byte)25);
										ParticleHelper.spawnAttackParticleAtEntity(this, this.getGuardedPos(0)+0.5D, this.getGuardedPos(1)+0.2D, this.getGuardedPos(2)+0.5D, (byte)8);
									}
								}
							}
							else
							{
								this.setStateMinor(ID.M.PlayerEID, -1);
							}
							
						}//end every 32 ticks
					}//end every 16 ticks
				}//end  every 8 ticks
			}//end every 2 ticks
		}//end client side
		
//		//TODO debug
//		if(this.ticksExisted % 32 == 0) {
//			LogHelper.info("AAAAAAAAAAAA "+this.worldObj.isRemote+" "+this.getStateMinor(ID.M.PlayerUID)+" "+this.getStateMinor(ID.M.PlayerEID)+" "+EntityHelper.getEntityPlayerByID(getStateMinor(ID.M.PlayerEID), 0, true));
//		}
	}

	/**
	 * update living entity
	 * 此方法在onUpdate中途呼叫
	 */
	@Override
	public void onLivingUpdate()
	{
//        //debug TODO
//        if (this.ticksExisted % 32 == 0)
//        {
//        	LogHelper.debug("AAAAAAAAAAAA "+this.world.isRemote+"  "+this.getPassengers().size()+" "+
//        					this.getRidingEntity());
//        	if (this.getRidingEntity() != null)
//        	{
//        		LogHelper.debug("BBBBBBBBBBBB "+this.world.isRemote+"  "+this.getRidingEntity().getPassengers().get(0));
//        	}
//        }
        
        //server side check
        if ((!world.isRemote))
        {
	    	//update movement, NOTE: 1.9.4: must done before vanilla MoveHelper updating in super.onLivingUpdate()
	    	EntityHelper.updateShipNavigator(this);
	        super.onLivingUpdate();
	        
        	//update target
        	TargetHelper.updateTarget(this);
        	
        	//update/init id
        	updateShipID();
        	
        	//timer ticking
        	updateServerTimer();
        	
        	//check every 8 ticks
        	if (ticksExisted % 8 == 0)
        	{
        		//update formation buff (fast update)
        		if (this.getUpdateFlag(ID.FU.FormationBuff)) calcEquipAndUpdateState();
        		
        		//reset AI and sync once
        		if (!this.initAI && ticksExisted > 10)
        		{
        			setStateFlag(ID.F.CanDrop, true);
            		clearAITasks();
            		clearAITargetTasks();		//reset AI for get owner after loading NBT data
            		setAIList();
            		setAITargetList();
            		decrGrudgeNum(0);			//check grudge
            		sendSyncPacketAllValue();	//sync packet to client
            		updateChunkLoader();
            		
            		this.initAI = true;
        		}
//        		LogHelper.info("DEBUG : check spawn: "+this.worldObj.getChunkProvider().getPossibleCreatures(EnumCreatureType.waterCreature, (int)posX, (int)posY, (int)posZ));
        		
        		//check every 16 ticks
            	if (ticksExisted % 16 == 0)
            	{
            		//waypoint move TODO review updateWaypointMove
            		if (!(this.getRidingEntity() instanceof BasicEntityMount))
            		{
            			if (EntityHelper.updateWaypointMove(this))
            			{
            				sendSyncPacket(S2CEntitySync.PID.SyncShip_Guard, true);
            			}
            		}
            		
            		//cancel mounts
            		if (this.canSummonMounts())
            		{
            			if (getStateEmotion(ID.S.State) < ID.State.EQUIP00)
            			{
          	  	  			//cancel riding
          	  	  			if (this.isRiding() && this.getRidingEntity() instanceof BasicEntityMount)
          	  	  			{
          	  	  				this.dismountRidingEntity();
          	  	  			}
          	  	  		}
            		}
            		
                	/** debug info */
//            		LogHelper.info("AAAAAAAA "+this.worldObj.getBlockMetadata(MathHelper.floor_double(posX), (int)posY, MathHelper.floor_double(posZ)));
//                	LogHelper.info("DEBUG : ship update: "+ServerProxy.getTeamData(900).getTeamBannedList());
//                	LogHelper.info("DEBUG : ship update: eid: "+ServerProxy.getNextShipID()+" "+ServerProxy.getNextPlayerID()+" "+ConfigHandler.nextPlayerID+" "+ConfigHandler.nextShipID);
//            		if(this.worldObj.provider.dimensionId == 0) {	//main world
//            			LogHelper.info("DEBUG : ship pos dim "+ClientProxy.getClientWorld().provider.dimensionId+" "+this.dimension+" "+this.posX+" "+this.posY+" "+this.posZ);
//            		}
//            		else {	//other world
//            			LogHelper.info("DEBUG : ship pos dim "+ClientProxy.getClientWorld().provider.dimensionId+" "+this.dimension+" "+this.posX+" "+this.posY+" "+this.posZ);
//            		}
            		
            		//check every 32 ticks
                	if (this.ticksExisted % 32 == 0)
                	{
                		//use bucket automatically
                		if ((getMaxHealth() - getHealth()) > (getMaxHealth() * 0.1F + 5F))
                		{
        	                if (decrSupplies(7))
        	                {
        		                this.heal(this.getMaxHealth() * 0.08F + 15F);	//1 bucket = 5% hp for large ship
        	                
        		                //airplane++
        		                if (this instanceof BasicEntityShipCV)
        		                {
        		                	BasicEntityShipCV ship = (BasicEntityShipCV) this;
        		                	ship.setNumAircraftLight(ship.getNumAircraftLight() + 1);
        		                	ship.setNumAircraftHeavy(ship.getNumAircraftHeavy() + 1);
        		                }
        	                }
        	            }
                		
                		//check every 64 ticks
                		if (ticksExisted % 64 == 0)
                		{
                			//sync model display
                			sendSyncPacketEmotion();

                			//check every 128 ticks
                        	if (ticksExisted % 128 == 0)
                        	{
                        		//delayed init, waiting for player entity loaded
                        		if (!this.initWaitAI && ticksExisted >= 128)
                        		{
                        			setUpdateFlag(ID.FU.FormationBuff, true);  //set update formation buff
                        			this.initWaitAI = true;
                        		}

                        		//check owner online
                        		if (this.getPlayerUID() > 0)
                        		{
                        			//get owner
                        			EntityPlayer player = EntityHelper.getEntityPlayerByUID(this.getPlayerUID());

                        			//owner exists (online and same world)
                        			if (player != null)
                        			{
                    					//update owner entity id (could be changed when owner change dimension or dead)
                            			this.setStateMinor(ID.M.PlayerEID, player.getEntityId());
                            			//sync guard position
                            			this.sendSyncPacket(S2CEntitySync.PID.SyncShip_Guard, false);
                            		}
                        		}
                        		
                        		//use combat ration automatically
                        		if (getStateMinor(ID.M.Morale) < 2100 && getFoodSaturation() < getFoodSaturationMax())
                        		{
                        			useCombatRation();
                        		}
                        		
                        		//update hp state
                        		updateEmotionState();
                        		
                        		//update mount
                        		updateMountSummon();
                        		
                        		//update consume item
                        		updateConsumeItem();
                        		
                        		//update morale value
                        		if (!getStateFlag(ID.F.NoFuel)) updateMorale();
                        		
                        		//check every 256 ticks
                            	if (this.ticksExisted % 256 == 0)
                            	{
                            		//update buff (slow update)
                            		calcEquipAndUpdateState();
                            		
                            		//show idle emotes
                            		if (!getStateFlag(ID.F.NoFuel)) applyEmotesReaction(4);
                            		
                            		//HP auto regen
                            		if (this.getHealth() < this.getMaxHealth())
                            		{
                            			this.setHealth(this.getHealth() + this.getMaxHealth() * 0.015625F + 1);
                            		}
                            		
                            		//food saturation--
                            		int f = this.getFoodSaturation();
                            		if (f > 0)
                            		{
                            			this.setFoodSaturation(--f);
                            		}
                            	}//end every 256 ticks
                        	}//end every 128 ticks
                		}//end every 64 ticks
                	}//end every 32 ticks
            	}//end every 16 ticks
        	}//end every 8 ticks
        	
        	//play timekeeping sound
        	if (ConfigHandler.timeKeeping && this.getStateFlag(ID.F.TimeKeeper))
        	{
        		int checkHour = getWorldHourTime();
        		if (checkHour >= 0) playTimeSound(checkHour);
        	}//end timekeeping
        	
        }//end server side
        //client side
        else
        {
        	super.onLivingUpdate();
        	
        	//update client side timer
        	updateClientTimer();
        }
    }
	
	//use combat ration
	protected void useCombatRation()
	{
		//search item in ship inventory
		int i = findItemInSlot(new ItemStack(ModItems.CombatRation), true);
		
		if (i >= 0)
		{
			//decr item stacksize
			ItemStack getItem = this.itemHandler.getStackInSlot(i);
			
			interactFeed(null, getItem);
			
			getItem.stackSize--;
			
			if (getItem.stackSize <= 0)
			{
				getItem = null;
			}
			
			//save back itemstack
			//no need to sync because no GUI opened
			this.itemHandler.setStackInSlot(i, getItem);
		}
	}

	//melee attack method, no ammo cost, no attack speed, damage = 12.5% atk
	@Override
	public boolean attackEntityAsMob(Entity target)
	{
		//get attack value
		float atk = getAttackBaseDamage(0, target);
		
		//experience++
		addShipExp(ConfigHandler.expGain[0]);
		
		//morale--
		decrMorale(0);
		setCombatTick(this.ticksExisted);
				
	    //entity attack effect
	    applySoundAtAttacker(0, target);
	    applyParticleAtAttacker(0, target, new float[4]);
	    
	    //是否成功傷害到目標
	    boolean isTargetHurt = target.attackEntityFrom(DamageSource.causeMobDamage(this), atk);

	    //target attack effect
	    if (isTargetHurt)
	    {
	    	applySoundAtTarget(0, target);
	        applyParticleAtTarget(0, target, new float[4]);
			applyEmotesReaction(3);
			
			if (ConfigHandler.canFlare)
			{
				flareTarget(target);
			}
	    }

	    return isTargetHurt;
	}
	
	//range attack method, cost light ammo, attack delay = 20 / attack speed, damage = 100% atk 
	@Override
	public boolean attackEntityWithAmmo(Entity target)
	{
		//get attack value
		float atk = getAttackBaseDamage(1, target);
        
        //experience++
  		addShipExp(ConfigHandler.expGain[1]);
  		
  		//grudge--
  		decrGrudgeNum(ConfigHandler.consumeGrudgeAction[ID.ShipConsume.LAtk]);
  		
  		//morale--
  		decrMorale(1);
  		setCombatTick(this.ticksExisted);
  		
        //light ammo -1
        if (!decrAmmoNum(0, this.getAmmoConsumption())) return false;
        
        //calc dist to target
        float[] distVec = new float[4];  //x, y, z, dist
        distVec[0] = (float) (target.posX - this.posX);
        distVec[1] = (float) (target.posY - this.posY);
        distVec[2] = (float) (target.posZ - this.posZ);
        distVec[3] = MathHelper.sqrt(distVec[0]*distVec[0] + distVec[1]*distVec[1] + distVec[2]*distVec[2]);
        
        distVec[0] = distVec[0] / distVec[3];
        distVec[1] = distVec[1] / distVec[3];
        distVec[2] = distVec[2] / distVec[3];
        
        //play cannon fire sound at attacker
        applySoundAtAttacker(1, target);
	    applyParticleAtAttacker(1, target, distVec);

        //calc miss chance, if not miss, calc cri/multi hit
        float missChance = 0.2F + 0.15F * (distVec[3] / StateFinal[ID.HIT]) - 0.001F * StateMinor[ID.M.ShipLevel];
        
        missChance -= EffectEquip[ID.EF_MISS];		//equip miss reduce
        if (missChance > 0.35F) missChance = 0.35F;	//max miss chance
        
        //calc miss -> crit -> double -> tripple
  		if (rand.nextFloat() < missChance)
  		{
          	atk = 0F;	//still attack, but no damage
          	applyParticleSpecialEffect(0);
  		}
  		else
  		{
  			//roll cri -> roll double hit -> roll triple hit (triple hit more rare)
  			//calc critical
          	if (rand.nextFloat() < this.getEffectEquip(ID.EF_CRI))
          	{
          		atk *= 1.5F;
          		applyParticleSpecialEffect(1);
          	}
          	else
          	{
          		//calc double hit
              	if (rand.nextFloat() < this.getEffectEquip(ID.EF_DHIT))
              	{
              		atk *= 2F;
              		applyParticleSpecialEffect(2);
              	}
              	else
              	{
              		//calc triple hit
                  	if (rand.nextFloat() < this.getEffectEquip(ID.EF_THIT))
                  	{
                  		atk *= 3F;
                  		applyParticleSpecialEffect(3);
                  	}
              	}
          	}
  		}
  		
  		//calc damage to player
  		if (target instanceof EntityPlayer)
  		{
  			atk *= 0.25F;
    			
  			//check friendly fire
      		if (!ConfigHandler.friendlyFire)
      		{
      			atk = 0F;
      		}
      		else if (atk > 59F)
      		{
      			atk = 59F;	//same with TNT
      		}
  		}
      		
	    //將atk跟attacker傳給目標的attackEntityFrom方法, 在目標class中計算傷害
	    //並且回傳是否成功傷害到目標
	    boolean isTargetHurt = target.attackEntityFrom(DamageSource.causeMobDamage(this).setProjectile(), atk);

	    //if attack success
	    if (isTargetHurt)
	    {
	    	applySoundAtTarget(1, target);
	        applyParticleAtTarget(1, target, distVec);
	        applyEmotesReaction(3);
	        
	        if (ConfigHandler.canFlare)
	        {
				flareTarget(target);
			}
        }

	    return isTargetHurt;
	}

	//range attack method, cost heavy ammo, attack delay = 100 / attack speed, damage = 500% atk
	@Override
	public boolean attackEntityWithHeavyAmmo(Entity target)
	{
		//get attack value
		float atk = getAttackBaseDamage(2, target);
		float kbValue = 0.15F;
		
		//飛彈是否採用直射
		boolean isDirect = false;
		float launchPos = (float) posY + height * 0.75F;
		
		//計算目標距離
		float[] distVec = new float[4];
		float tarX = (float) target.posX;
		float tarY = (float) target.posY;
		float tarZ = (float) target.posZ;
		
		distVec[0] = tarX - (float) this.posX;
        distVec[1] = tarY - (float) this.posY;
        distVec[2] = tarZ - (float) this.posZ;
		distVec[3] = MathHelper.sqrt(distVec[0]*distVec[0] + distVec[1]*distVec[1] + distVec[2]*distVec[2]);
        
        //超過一定距離/水中 , 則採用拋物線,  在水中時發射高度較低
        if (distVec[3] < 5F)
        {
        	isDirect = true;
        }
        
        if(getShipDepth() > 0D)
        {
        	isDirect = true;
        	launchPos = (float) posY;
        }
		
		//experience++
		addShipExp(ConfigHandler.expGain[2]);
		
		//grudge--
		decrGrudgeNum(ConfigHandler.consumeGrudgeAction[ID.ShipConsume.HAtk]);
		
  		//morale--
		decrMorale(2);
  		setCombatTick(this.ticksExisted);
	
		//play attack effect
        applySoundAtAttacker(2, target);
	    applyParticleAtAttacker(2, target, distVec);
        
        //heavy ammo--
        if(!decrAmmoNum(1, this.getAmmoConsumption())) return false;
        
        //calc miss chance, miss: add random offset(0~6) to missile target 
        float missChance = 0.2F + 0.15F * (distVec[3] / StateFinal[ID.HIT]) - 0.001F * StateMinor[ID.M.ShipLevel];
        missChance -= EffectEquip[ID.EF_MISS];	//equip miss reduce
        if (missChance > 0.35F) missChance = 0.35F;	//max miss chance = 30%
       
        if (this.rand.nextFloat() < missChance)
        {
        	tarX = tarX - 5F + this.rand.nextFloat() * 10F;
        	tarY = tarY + this.rand.nextFloat() * 5F;
        	tarZ = tarZ - 5F + this.rand.nextFloat() * 10F;
        	
        	applyParticleSpecialEffect(0);  //miss particle
        }
        
        //spawn missile
        EntityAbyssMissile missile = new EntityAbyssMissile(this.world, this, 
        		tarX, tarY+target.height*0.2F, tarZ, launchPos, atk, kbValue, isDirect, -1F);
        this.world.spawnEntity(missile);
        
        //play target effect
        applySoundAtTarget(2, target);
        applyParticleAtTarget(2, target, distVec);
        applyEmotesReaction(3);
        
        if (ConfigHandler.canFlare)
        {
			flareTarget(target);
		}
        
        return true;
	}
	
	//be attacked method, 包括其他entity攻擊, anvil攻擊, arrow攻擊, fall damage都使用此方法 
	@Override
    public boolean attackEntityFrom(DamageSource attacker, float atk)
	{
		//set hurt face
    	if (this.getStateEmotion(ID.S.Emotion) != ID.Emotion.O_O)
    	{
    		this.setStateEmotion(ID.S.Emotion, ID.Emotion.O_O, true);
    	}
    	
    	//change sensitive body
  		if (this.rand.nextInt(20) == 0) randomSensitiveBody();
  		
    	//若攻擊方為owner, 則直接回傳傷害, 不計def跟friendly fire
		if (attacker.getEntity() instanceof EntityPlayer &&
			TeamHelper.checkSameOwner(attacker.getEntity(), this))
		{
			this.setSitting(false);
			return super.attackEntityFrom(attacker, atk);
		}
        
        //若掉到世界外, 則傳送回y=4
        if (attacker.getDamageType().equals("outOfWorld"))
        {
        	//取消坐下動作
			this.setSitting(false);
			this.dismountRidingEntity();
        	this.setPositionAndUpdate(this.posX, 4D, this.posZ);
        	this.motionX = 0D;
        	this.motionY = 1D;
        	this.motionZ = 0D;
        	return false;
        }
        
        //無敵的entity傷害無效
		if (this.isEntityInvulnerable(attacker))
		{
            return false;
        }
		else if (attacker.getEntity() != null)
		{	//不為null才算傷害, 可免疫毒/掉落/窒息等傷害
			Entity entity = attacker.getEntity();
			
			//不會對自己造成傷害, 可免疫毒/掉落/窒息等傷害 (此為自己對自己造成傷害)
			if (entity.equals(this))
			{
				//取消坐下動作
				this.setSitting(false);
				return false;
			}
			
			//若攻擊方為player, 則檢查friendly fire
			if (entity instanceof EntityPlayer)
			{
				//若禁止friendlyFire, 則不造成傷害
				if (!ConfigHandler.friendlyFire)
				{
					return false;
				}
			}
			
			//進行dodge計算
			float dist = (float) this.getDistanceSqToEntity(entity);
			if (EntityHelper.canDodge(this, dist))
			{
				return false;
			}
			
			//進行def計算
			float reduceAtk = atk * (1F - (StateFinal[ID.DEF] - rand.nextInt(20) + 10F) / 100F);    
			
			//ship vs ship, config傷害調整
			if (entity instanceof BasicEntityShip || entity instanceof BasicEntityAirplane || 
				entity instanceof EntityRensouhou || entity instanceof BasicEntityMount)
			{
				reduceAtk = reduceAtk * (float)ConfigHandler.dmgSvS * 0.01F;
			}
			
			//ship vs ship, damage type傷害調整
			if (entity instanceof IShipAttackBase)
			{
				//get attack time for damage modifier setting (day, night or ...etc)
				int modSet = this.world.provider.isDaytime() ? 0 : 1;
				reduceAtk = CalcHelper.calcDamageByType(reduceAtk, ((IShipAttackBase) entity).getDamageType(), this.getDamageType(), modSet);
			}
			
			//min damage設為1
	        if (reduceAtk < 1) reduceAtk = 1;

			//取消坐下動作
			this.setSitting(false);
			
			//設置revenge target
			this.setEntityRevengeTarget(entity);
			this.setEntityRevengeTime();
//			LogHelper.info("DEBUG : set revenge target: "+entity+"  host: "+this);
			
			//若傷害力可能致死, 則尋找物品中有無repair goddess來取消掉此攻擊
			if (reduceAtk >= (this.getHealth() - 1F))
			{
				if (this.decrSupplies(8))
				{
					this.setHealth(this.getMaxHealth());
					this.StateTimer[ID.T.ImmuneTime] = 60;
					
					//TODO add repair goddess particle
					
					return false;
				}
			}
			
	  		//morale--
			decrMorale(5);
	  		setCombatTick(this.ticksExisted);
	  		
	  		//set damaged body ID and show emotes
	  		if (this.rand.nextInt(5) == 0)
	  		{
				//set hit position
				this.setStateMinor(ID.M.HitHeight, CalcHelper.getEntityHitHeight(this, attacker.getSourceOfDamage()));
				this.setStateMinor(ID.M.HitAngle, CalcHelper.getEntityHitSide(this, attacker.getSourceOfDamage()));
				
				//apply emotes
				applyEmotesReaction(2);
	  		}
   
            //執行父class的被攻擊判定, 包括重置love時間, 計算火毒抗性, 計算鐵砧/掉落傷害, 
            //hurtResistantTime(0.5sec無敵時間)計算, 
            return super.attackEntityFrom(attacker, reduceAtk);
        }
		
		return false;
    }
	
	/** decr morale value, type: 0:melee, 1:light, 2:heavy, 3:light air, 4:light heavy, 5:damaged */
	public void decrMorale(int type)
	{
		switch (type)
		{
		case 0:  //melee
			setStateMinor(ID.M.Morale, getStateMinor(ID.M.Morale) - 2);
			break;
		case 1:  //light
			setStateMinor(ID.M.Morale, getStateMinor(ID.M.Morale) - 4);
			break;
		case 2:  //heavy
			setStateMinor(ID.M.Morale, getStateMinor(ID.M.Morale) - 6);
			break;
		case 3:  //light air
			setStateMinor(ID.M.Morale, getStateMinor(ID.M.Morale) - 6);
			break;
		case 4:  //light heavy
			setStateMinor(ID.M.Morale, getStateMinor(ID.M.Morale) - 8);
			break;
		case 5:  //damaged
			setStateMinor(ID.M.Morale, getStateMinor(ID.M.Morale) - 5);
			break;
		}
	}
	
	/** decr ammo, type: 0:light, 1:heavy */
	protected boolean decrAmmoNum(int type, int amount)
	{
		int ammoType = ID.M.NumAmmoLight;
		boolean useItem = !hasAmmoLight();
		boolean showEmo = false;
		
		switch (type)
		{
		case 1:   //use heavy ammo
			ammoType = ID.M.NumAmmoHeavy;
			useItem = !hasAmmoHeavy();
			break;
		}

		//check ammo first time
		if (StateMinor[ammoType] <= amount || useItem)
		{
			int addAmmo = 0;
			
			//use light ammo item
			if (ammoType == ID.M.NumAmmoLight)
			{
				if (decrSupplies(0))
				{  //use ammo item
					addAmmo = Values.N.BaseLightAmmo;
					showEmo = true;
				}
				else if (decrSupplies(2))
				{  //use ammo container item
					addAmmo = Values.N.BaseLightAmmo * 9;
					showEmo = true;
				}
			}
			//use heavy ammo item
			else
			{
				if (decrSupplies(1))
				{  //use ammo item
					addAmmo = Values.N.BaseHeavyAmmo;
					showEmo = true;
				}
				else if (decrSupplies(3))
				{  //use ammo container item
					addAmmo = Values.N.BaseHeavyAmmo * 9;
					showEmo = true;
				}
			}
			
			//check easy mode
			if (ConfigHandler.easyMode)
			{
				addAmmo *= 10;
			}
			
			StateMinor[ammoType] += addAmmo;
		}
		
		//show emotes
		if (showEmo)
		{
			if (this.getEmotesTick() <= 0)
			{
				this.setEmotesTick(40);
				
				switch (this.rand.nextInt(4))
				{
				case 1:
					applyParticleEmotion(29);  //blink
					break;
				case 2:
					applyParticleEmotion(30);  //pif
					break;
				default:
					applyParticleEmotion(9);  //hungry
					break;
				}
			}
		}
		
		//check ammo second time
		if (StateMinor[ammoType] < amount)
		{
			//show emotes
			if (this.getEmotesTick() <= 0)
			{
				this.setEmotesTick(20);
				
				switch (this.rand.nextInt(7))
				{
				case 1:
					applyParticleEmotion(0);  //drop
					break;
				case 2:
					applyParticleEmotion(2);  //panic
					break;
				case 3:
					applyParticleEmotion(5);  //...
					break;
				case 4:
					applyParticleEmotion(20);  //orz
					break;
				default:
					applyParticleEmotion(32);  //hmm
					break;
				}
			}
			
			return false;
		}
		else
		{
			StateMinor[ammoType] -= amount;
			return true;
		}
	}
	
	//eat grudge and change movement speed
	protected void decrGrudgeNum(int par1)
	{
		//limit max cost per checking
		if (par1 > 500)
		{
			par1 = 500;
		}
		
		//check fuel flag
		if (!getStateFlag(ID.F.NoFuel))
		{  //has fuel
			StateMinor[ID.M.NumGrudge] -= par1;
		}

		//eat one grudge if fuel <= 0
		if (StateMinor[ID.M.NumGrudge] <= 0)
		{
			//try to find grudge
			if (decrSupplies(4))
			{		//find grudge
				if (ConfigHandler.easyMode)
				{
					StateMinor[ID.M.NumGrudge] += Values.N.BaseGrudge * 10;
				}
				else
				{
					StateMinor[ID.M.NumGrudge] += Values.N.BaseGrudge;
				}
			}
			else
			{
				if (decrSupplies(5))  //find grudge block
				{
					if (ConfigHandler.easyMode)
					{
						StateMinor[ID.M.NumGrudge] += Values.N.BaseGrudge * 90;
					}
					else
					{
						StateMinor[ID.M.NumGrudge] += Values.N.BaseGrudge * 9;
					}
				}
			}
			//避免吃掉含有儲存資訊的方塊, 因此停用heavy grudge block作為補充道具
		}
		
		//check fuel again and set fuel flag
		if (StateMinor[ID.M.NumGrudge] <= 0)
		{
			setStateFlag(ID.F.NoFuel, true);
		}
		else
		{
			setStateFlag(ID.F.NoFuel, false);
		}
		
		//check fuel flag and set AI
		if (getStateFlag(ID.F.NoFuel))  //no fuel, clear AI
		{
			//原本有AI, 則清除之
			if (this.targetTasks.taskEntries.size() > 0)
			{
				updateFuelState(true);
			}	
		}
		else							//has fuel, set AI
		{
			if (this.targetTasks.taskEntries.size() < 1)
			{
				updateFuelState(false);
			}
		}
	}
	
	//decrese ammo/grudge/repair item, return true or false(not enough item)
	protected boolean decrSupplies(int type)
	{
		int itemNum = 1;
		boolean noMeta = false;
		ItemStack itemType = null;
		
		//find ammo
		switch (type)
		{
		case 0:	//use 1 light ammo
			itemType = new ItemStack(ModItems.Ammo,1,0);
			break;
		case 1: //use 1 heavy ammo
			itemType = new ItemStack(ModItems.Ammo,1,2);
			break;
		case 2:	//use 1 light ammo container
			itemType = new ItemStack(ModItems.Ammo,1,1);
			break;
		case 3: //use 1 heavy ammo container
			itemType = new ItemStack(ModItems.Ammo,1,3);
			break;
		case 4: //use 1 grudge
			itemType = new ItemStack(ModItems.Grudge,1);
			break;
		case 5: //use 1 grudge block
			itemType = new ItemStack(ModBlocks.BlockGrudge,1);
			break;
		case 6: //use 1 grudge block
			itemType = new ItemStack(ModBlocks.BlockGrudgeHeavy,1);
			break;
		case 7:	//use 1 repair bucket
			itemType = new ItemStack(ModItems.BucketRepair,1);
			break;
		case 8:	//use 1 repair goddess
			itemType = new ItemStack(ModItems.RepairGoddess,1);
			break;
		}
		
		//search item in ship inventory
		int i = findItemInSlot(itemType, noMeta);
		
		if (i == -1)
		{		//item not found
			return false;
		}
		
		//decr item stacksize
		ItemStack getItem = this.itemHandler.getStackInSlot(i);

		if (getItem.stackSize >= itemNum)
		{
			getItem.stackSize -= itemNum;
		}
		else
		{	//not enough item, return false
			return false;
		}
				
		if (getItem.stackSize == 0)
		{
			getItem = null;
		}
		
		//save back itemstack
		//no need to sync because no GUI opened
		this.itemHandler.setStackInSlot(i, getItem);
		
		return true;	
	}

	//update AI task when no fuel
	protected void updateFuelState(boolean nofuel)
	{
		if (nofuel)
		{
			setStateEmotion(ID.S.Emotion, ID.Emotion.HUNGRY, false);
			setStateMinor(ID.M.Morale, 0);
			clearAITasks();
			clearAITargetTasks();
			sendSyncPacketAllValue();
			
			//設定mount的AI
			if (this.getRidingEntity() instanceof BasicEntityMount)
			{
				((BasicEntityMount) this.getRidingEntity()).clearAITasks();
			}
			
			//show no fuel emotes
			if (this.getEmotesTick() <= 0)
			{
				this.setEmotesTick(20);
				switch (this.rand.nextInt(7))
				{
				case 1:
					applyParticleEmotion(0);  //drop
					break;
				case 2:
					applyParticleEmotion(32);  //hmm
					break;
				case 3:
					applyParticleEmotion(2);  //panic
					break;
				case 4:
					applyParticleEmotion(12);  //omg
					break;
				case 5:
					applyParticleEmotion(5);  //...
					break;
				case 6:
					applyParticleEmotion(20);  //orz
					break;
				default:
					applyParticleEmotion(10);  //dizzy
					break;
				}
			}
		}
		else
		{
			setStateEmotion(ID.S.Emotion, ID.Emotion.NORMAL, false);
			clearAITasks();
			clearAITargetTasks();
			setAIList();
			setAITargetList();
			sendSyncPacketAllValue();
			
			//設定mount的AI
			if (this.getRidingEntity() instanceof BasicEntityMount)
			{
				((BasicEntityMount) this.getRidingEntity()).clearAITasks();
				((BasicEntityMount) this.getRidingEntity()).setAIList();
			}
			
			//show recovery emotes
			if (this.getEmotesTick() <= 0)
			{
				this.setEmotesTick(40);
				switch (this.rand.nextInt(5))
				{
				case 1:
					applyParticleEmotion(31);  //shy
					break;
				case 2:
					applyParticleEmotion(32);  //hmm
					break;
				case 3:
					applyParticleEmotion(7);  //note
					break;
				default:
					applyParticleEmotion(1);  //love
					break;
				}
			}
		}
	}

	//find item in ship inventory
	protected int findItemInSlot(ItemStack parItem, boolean noMeta)
	{
		ItemStack slotitem = null;

		//search ship inventory (except equip slots)
		for (int i = ContainerShipInventory.SLOTS_SHIPINV; i < CapaShipInventory.SlotMax; i++)
		{
			//check inv size
			switch (getInventoryPageSize())
			{
			case 0:
				if (i >= ContainerShipInventory.SLOTS_SHIPINV + 18) return -1;
				break;
			case 1:
				if (i >= ContainerShipInventory.SLOTS_SHIPINV + 36) return -1;
				break;
			}
			
			//get item
			slotitem = this.itemHandler.getStackInSlot(i);
			
			if (slotitem != null && slotitem.getItem().equals(parItem.getItem()))
			{
				if (noMeta)
				{
					return i;	//found item
				}
				else
				{
					if (slotitem.getItemDamage() == parItem.getItemDamage())
					{
						return i;
					}
				}
				
			}		
		}	
		
		return -1;	//item not found
	}
	
	@Override
	public boolean canFly()
	{
		return isPotionActive(MobEffects.LEVITATION);
	}
	
	@Override
	public boolean canBreatheUnderwater()
	{
		return true;
	}
	
	//true if use mounts
	public boolean canSummonMounts()
	{
		return false;
	}
	
	public BasicEntityMount summonMountEntity()
	{
		return null;
	}
	
	@Override
	public Entity getGuardedEntity()
	{
		return this.guardedEntity;
	}

	@Override
	public void setGuardedEntity(Entity entity)
	{
		if(entity != null && entity.isEntityAlive())
		{
			this.guardedEntity = entity;
			this.setStateMinor(ID.M.GuardID, entity.getEntityId());
		}
		else
		{
			this.guardedEntity = null;
			this.setStateMinor(ID.M.GuardID, -1);
		}
	}

	@Override
	public int getGuardedPos(int vec)
	{
		switch (vec)
		{
		case 0:
			return this.getStateMinor(ID.M.GuardX);
		case 1:
			return this.getStateMinor(ID.M.GuardY);
		case 2:
			return this.getStateMinor(ID.M.GuardZ);
		case 3:
			return this.getStateMinor(ID.M.GuardDim);
		default:
			return this.getStateMinor(ID.M.GuardType);
		}
	}

	/**
	 *  type: 0:none, 1:block, 2:entity
	 */
	@Override
	public void setGuardedPos(int x, int y, int z, int dim, int type)
	{
		this.setStateMinor(ID.M.GuardX, x);
		this.setStateMinor(ID.M.GuardY, y);
		this.setStateMinor(ID.M.GuardZ, z);
		this.setStateMinor(ID.M.GuardDim, dim);
		this.setStateMinor(ID.M.GuardType, type);
	}
	
	@Override
	public double getMountedYOffset()
	{
		return this.height;
	}
	
	@Override
	public Entity getHostEntity()
	{
		if (this.getPlayerUID() > 0)
		{
			return EntityHelper.getEntityPlayerByUID(this.getPlayerUID());
		}
		else
		{
			return this.getOwner();
		}
	}
	
	@Override
	public int getDamageType()
	{
		return this.getStateMinor(ID.M.DamageType);
	}
	
//	//set slot 6 as held item 
//	@Override
//	public ItemStack getHeldItem() {
//		if(ExtProps != null && ExtProps.slots != null) {
//			return ExtProps.slots[6];
//		}
//		
//		return super.getHeldItem();
//	}
	
	//update ship id
	protected void updateShipID()
	{
		//register or update ship id and owner id
		if (!this.isUpdated && ticksExisted % updateTime == 0)
		{
			LogHelper.info("DEBUG : update ship: initial SID, PID  cd: "+updateTime);
			ServerProxy.updateShipID(this);				//update ship uid
			
			if (this.getPlayerUID() <= 0)
			{
				ServerProxy.updateShipOwnerID(this);	//update owner uid
			}
			
			//update success
			if (getPlayerUID() > 0 && getShipUID() > 0)
			{
				this.sendSyncPacketAllValue();
				this.isUpdated = true;
			}
			
			//prolong update time
			if (updateTime >= 4096)
			{
				updateTime = 4096;
			}
			else
			{
				updateTime *= 2;
			}
		}//end update id
	}
	
	//不跟aircraft, mount, rider碰撞
	@Override
  	protected void collideWithEntity(Entity target)
	{
  		if (target instanceof BasicEntityAirplane || target.equals(this.getRidingEntity()))
  		{
  			return;
  		}
  		
  		for (Entity p : this.getPassengers())
  		{
  			if (target.equals(p)) return;
  		}
  		
  		target.applyEntityCollision(this);
    }
  	
  	//check state final limit
  	protected float[] checkStateFinalLimit(float[] par1)
  	{
  		//max cap
  		for (int i = 0; i < 6; i++)
  		{
  			if (ConfigHandler.limitShipBasic[i] >= 0D && par1[i] > ConfigHandler.limitShipBasic[i])
  			{
  				par1[i] = (float) ConfigHandler.limitShipBasic[i];
  			}
  		}
		
		if (ConfigHandler.limitShipBasic[ID.ATK] >= 0D && par1[ID.ATK_H] > ConfigHandler.limitShipBasic[ID.ATK])
		{
			par1[ID.ATK_H] = (float) ConfigHandler.limitShipBasic[ID.ATK];
		}
		if (ConfigHandler.limitShipBasic[ID.ATK] >= 0D && par1[ID.ATK_AL] > ConfigHandler.limitShipBasic[ID.ATK])
		{
			par1[ID.ATK_AL] = (float) ConfigHandler.limitShipBasic[ID.ATK];
		}
		if (ConfigHandler.limitShipBasic[ID.ATK] >= 0D && par1[ID.ATK_AH] > ConfigHandler.limitShipBasic[ID.ATK])
		{
			par1[ID.ATK_AH] = (float) ConfigHandler.limitShipBasic[ID.ATK];
		}

		//min cap
		if (par1[ID.HP] < 1F)
		{
			par1[ID.HP] = 1F;
		}
		
		if (par1[ID.HIT] < 1F)
		{
			par1[ID.HIT] = 1F;
		}
		
		if (par1[ID.SPD] < 0.1F)
		{
			par1[ID.SPD] = 0.1F;
		}
		
		if (par1[ID.MOV] < 0F)
		{
			par1[ID.MOV] = 0F;
		}
		
		return par1;
  	}
  	
	//check equip effect limit
  	protected float[] checkEffectEquipLimit(float[] par1)
  	{
  		//max cap
  		for (int i = 0; i < 7; i++)
  		{
  			if (i != ID.EF_AA && i != ID.EF_ASM && i != ID.EF_DODGE)
  			{
  				if (ConfigHandler.limitShipEffect[i] >= 0D && par1[i] > ConfigHandler.limitShipEffect[i] * 0.01F)
  				{
  	  				par1[i] = (float) ConfigHandler.limitShipEffect[i] * 0.01F;
  	  			}
  			}
  			else
  			{
  				if (ConfigHandler.limitShipEffect[i] >= 0D && par1[i] > ConfigHandler.limitShipEffect[i])
  				{
  	  				par1[i] = (float) ConfigHandler.limitShipEffect[i];
  	  			}
  			}
  		}
  		
  		//min cap
  		for (int i = 0; i < 7; i++)
  		{
			if (par1[i] < 0F)
			{
  				par1[i] = 0F;
  			}
  		}
		
		return par1;
  	}

  	//update hp state
  	protected void updateEmotionState()
  	{
  		float hpState = this.getHealth() / this.getMaxHealth();
		
		//check hp state
		if (hpState > 0.75F)
		{	//normal
			this.setStateEmotion(ID.S.HPState, ID.HPState.NORMAL, false);
		}
		else if (hpState > 0.5F)
		{	//minor damage
			this.setStateEmotion(ID.S.HPState, ID.HPState.MINOR, false);
		}
		else if (hpState > 0.25F)
		{	//moderate damage
			this.setStateEmotion(ID.S.HPState, ID.HPState.MODERATE, false);   			
		}
		else
		{	//heavy damage
			this.setStateEmotion(ID.S.HPState, ID.HPState.HEAVY, false);
		}
		
		//roll emtion: hungry > T_T > bored > O_O
		if (getStateFlag(ID.F.NoFuel))
		{
			if (this.getStateEmotion(ID.S.Emotion) != ID.Emotion.HUNGRY)
			{
				this.setStateEmotion(ID.S.Emotion, ID.Emotion.HUNGRY, false);
			}
		}
		else
		{
			if (hpState < 0.5F)
			{
    			if (this.getStateEmotion(ID.S.Emotion) != ID.Emotion.T_T)
    			{
    				this.setStateEmotion(ID.S.Emotion, ID.Emotion.T_T, false);
    			}			
    		}
			else
			{
				if (this.getRNG().nextInt(2) == 0) {	//50% for bored
	    			if (this.getStateEmotion(ID.S.Emotion) != ID.Emotion.BORED)
	    			{
	    				this.setStateEmotion(ID.S.Emotion, ID.Emotion.BORED, false);
	    			}
	    		}
	    		else
	    		{	//back to normal face
	    			if (this.getStateEmotion(ID.S.Emotion) != ID.Emotion.NORMAL)
	    			{
	    				this.setStateEmotion(ID.S.Emotion, ID.Emotion.NORMAL, false);
	    			}
	    		}
			}
		}
		
		//sync emotion
		this.sendSyncPacketEmotion();
  	}
  	
	//update hp state
  	protected void updateMountSummon()
  	{
    	if (this.canSummonMounts())
    	{
    		//summon mount if emotion state >= equip00
  	  		if (getStateEmotion(ID.S.State) >= ID.State.EQUIP00)
  	  		{
  	  			if (!this.isRiding())
  	  			{
  	  				//summon mount entity
  	  	  			BasicEntityMount mount = this.summonMountEntity();
  	  	  			this.world.spawnEntity(mount);
  	  	  			
  	  	  			//clear rider
  	  	  			for (Entity p : this.getPassengers())
  	  	  			{
  	  	  				p.dismountRidingEntity();
  	  	  			}
  	  	  			
  	  	  			//set riding entity
	  	  			this.startRiding(mount, true);
	  	  			
	  	  			//sync rider
	  	  			mount.sendSyncPacket(0);
  	  			}
  	  		}
    	}
  	}
  	
  	//update consume item
  	protected void updateConsumeItem()
  	{
  		//set air value
		if (this.getAir() < 300)
		{
        	setAir(300);
        }
		
		//get ammo if no ammo
		if (!this.hasAmmoLight()) { this.decrAmmoNum(0, 0); }
		if (!this.hasAmmoHeavy()) { this.decrAmmoNum(1, 0); }
		
		//calc move distance
		double distX = posX - ShipPrevX;
		double distY = posY - ShipPrevY;
		double distZ = posZ - ShipPrevZ;
		
		//calc total consumption
    	int valueConsume = (int) MathHelper.sqrt(distX*distX + distY*distY + distZ*distZ);
    	if (ShipPrevY <= 0D) valueConsume = 0;  //do not decrGrudge if ShipPrev not inited
    	
    	//morale-- per 2 blocks
    	int m = (int)((float)valueConsume * 0.5F);
    	if(m < 5) m = 5;
    	if(m > 50) m = 50;
    	this.setStateMinor(ID.M.Morale, this.getStateMinor(ID.M.Morale) - m);
    	
    	//moving grudge cost per block
    	valueConsume *= ConfigHandler.consumeGrudgeAction[ID.ShipConsume.Move];
    	
//    	//get exp if transport TODO
//    	if(this instanceof EntityTransportWa && this.ticksExisted > 200)
//    	{
//    		//gain exp when moving
//    		int moveExp = (int) (valueConsume * 0.2F);
//    		addShipExp(moveExp);
//    	}
    	
    	//add idle grudge cost
    	valueConsume += this.getGrudgeConsumption();
    	
    	//eat grudge
    	decrGrudgeNum(valueConsume);
    	
    	//update pos
    	ShipPrevX = posX;
		ShipPrevY = posY;
		ShipPrevZ = posZ;
  	}
  	
  	//update formation buffs, SERVER SIDE ONLY
  	protected void updateFormationBuffs()
  	{
  		//check update flag
  		if (!world.isRemote)
  		{
  			CapaTeitoku capa = CapaTeitoku.getTeitokuCapability(this.getPlayerUID());
  			
  			if(capa != null)
  			{
  				//check ship is in formation team
  				int[] teamslot = capa.checkIsInFormationTeam(getShipUID());
  				
  				//if in team and can apply buff, set formation data
  				if (teamslot[0] >= 0 && teamslot[1] >= 0 && capa.getFormatID(teamslot[0]) > 0)
  				{
  					//set formation type and pos
  					setStateMinor(ID.M.FormatType, capa.getFormatID(teamslot[0]));
  					
  					//if diamond formation with 5 ships
  					if (this.getStateMinor(ID.M.FormatType) == 3 &&
  						capa.getNumberOfShip(teamslot[0]) == 5)
  					{
  						//diamond with 5 ship: formation position != team position
  						int slotID = capa.getFormationPos(teamslot[0], getShipUID());
  						
  						if (slotID >= 0)
  						{
  							setStateMinor(ID.M.FormatPos, slotID);
  						}
  					}
  					//other formation
  					else
  					{
  						setStateMinor(ID.M.FormatPos, teamslot[1]);
  					}
  					
  					//set buff value
  					setEffectFormation(FormationHelper.getFormationBuffValue(getStateMinor(ID.M.FormatType), getStateMinor(ID.M.FormatPos)));
  					setEffectFormationFixed(ID.FormationFixed.MOV, FormationHelper.getFormationMOV(capa, teamslot[0]));
  				}
  				//not in team, clear buff
  				else
  				{
  					//set formation type and pos
  					setStateMinor(ID.M.FormatType, 0);
  					setStateMinor(ID.M.FormatPos, -1);
  					
  					//reset buff value
  					setEffectFormation(Values.zeros13);
  					setEffectFormationFixed(ID.FormationFixed.MOV, 0F);
  				}
  			}

  			//set done flag
  			this.setUpdateFlag(ID.FU.FormationBuff, false);
  		}
  		
  		//apply formation buff
		if (this.getStateMinor(ID.M.FormatType) > 0)
		{
			//mul buff
			StateFinal[ID.ATK] = (EffectFormation[ID.Formation.ATK_L] * 0.01F + 1F) * StateFinal[ID.ATK];
			StateFinal[ID.ATK_H] = (EffectFormation[ID.Formation.ATK_H] * 0.01F + 1F) * StateFinal[ID.ATK_H];
			StateFinal[ID.ATK_AL] = (EffectFormation[ID.Formation.ATK_AL] * 0.01F + 1F) * StateFinal[ID.ATK_AL];
			StateFinal[ID.ATK_AH] = (EffectFormation[ID.Formation.ATK_AH] * 0.01F + 1F) * StateFinal[ID.ATK_AH];
			StateFinal[ID.DEF] = (EffectFormation[ID.Formation.DEF] * 0.01F + 1F) * StateFinal[ID.DEF];
			
			EffectEquip[ID.EF_CRI] = (EffectFormation[ID.Formation.CRI] * 0.01F + 1F) * EffectEquip[ID.EF_CRI];
			EffectEquip[ID.EF_DHIT] = (EffectFormation[ID.Formation.DHIT] * 0.01F + 1F) * EffectEquip[ID.EF_DHIT];
			EffectEquip[ID.EF_THIT] = (EffectFormation[ID.Formation.THIT] * 0.01F + 1F) * EffectEquip[ID.EF_THIT];
			EffectEquip[ID.EF_MISS] = (EffectFormation[ID.Formation.MISS] * 0.01F + 1F) * EffectEquip[ID.EF_MISS];
			EffectEquip[ID.EF_AA] = (EffectFormation[ID.Formation.AA] * 0.01F + 1F) * EffectEquip[ID.EF_AA];
			EffectEquip[ID.EF_ASM] = (EffectFormation[ID.Formation.ASM] * 0.01F + 1F) * EffectEquip[ID.EF_ASM];
			EffectEquip[ID.EF_DODGE] = (EffectFormation[ID.Formation.DODGE] * 0.01F + 1F) * EffectEquip[ID.EF_DODGE];
			
			//fixed buff
			StateFinal[ID.MOV] = getEffectFormationFixed(ID.FormationFixed.MOV);
		}
  	}
  	
  	/** morale value
  	 *  5101 - 8000  Exciting:  dodge/mov/spd++
  	 *  3901 - 5100  Happy:     cri/dhit/thit++
  	 *  2101 - 3900  Normal:    -
  	 *  901  - 2100  Tired:     dodge-- (no effect if dodge = 0)
  	 *  0    - 900   Exhausted: mov/spd--
  	 *  
  	 *  action:
  	 *  
  	 *  caress head:
  	 *    max 8000
  	 *    +10 / 3 ticks (4 ticks if mouse delay 200ms)
  	 *    
  	 *  feed:
  	 *    max 4800
  	 *    base +100 / +N / per heal amount and saturation
  	 *    
  	 *  idle:
  	 *    > 3900: -10 per 128 ticks (6000 -> 3900 in 22 min)
  	 *    < 2000: +10 per 128 ticks
  	 *    
  	 *  attack:
  	 *    -1 per hit
  	 *    +1 per kill
  	 *    
  	 *  damaged:
  	 *    -2 per hit
  	 *    
  	 *  move:
  	 *    -1 per 10 blocks, min -1  
  	 *  
  	 */
  	protected void updateMorale()
  	{
  		int m = this.getStateMinor(ID.M.Morale);
  		
  		//out of combat
  		if (EntityHelper.checkShipOutOfCombat(this))
  		{
  			if (m < 3000)
  			{	//take 9~11 min from 0 to 3000
  	  			this.setStateMinor(ID.M.Morale, m + 15);
  	  		}
  	  		else if (m > 3900)
  	  		{
  	  			this.setStateMinor(ID.M.Morale, m - 10);
  	  		}
  		}
  		//in combat
  		else
  		{
  	  		if (m < 900)
  	  		{
  	  			this.setStateMinor(ID.M.Morale, m - 8);
  	  		}
  	  		else if (m < 1800)
  	  		{
  	  			this.setStateMinor(ID.M.Morale, m - 6);
  	  		}
  	  		else if (m < 2700)
  	  		{
  	  			this.setStateMinor(ID.M.Morale, m - 5);
  	  		}
  	  		else if (m > 3900)
  	  		{
  	  			this.setStateMinor(ID.M.Morale, m - 10);
  	  		}
  		}
  		
  	}
  	
  	/** apply morale buffs
  	 * 
  	 *  morale   > 5100   > 3900   < 2101   < 901
  	 *    atk      121%     110%     90%      81%
  	 *    spd      +0.5     +0.5              -0.5    
  	 *    mov      +0.1     +0.1              -0.1
  	 *    hit      +4       +2       -2       -4
  	 *    dodge    +25      +10      -10      -25
  	 *    cri      +10%     +10%
  	 *    dhit     +10%     +10%
  	 *    thit     +10%     +10%
  	 */
  	protected void updateMoraleBuffs()
  	{
  		int m = this.getStateMinor(ID.M.Morale);
  		
  		if (m > 3900)
  		{
  			StateFinal[ID.ATK] *= 1.1F;
  			StateFinal[ID.ATK_H] *= 1.1F;
  			StateFinal[ID.ATK_AL] *= 1.1F;
  			StateFinal[ID.ATK_AH] *= 1.1F;
  			StateFinal[ID.HIT] += 2F;
  			EffectEquip[ID.EF_CRI] += 0.1F;
  			EffectEquip[ID.EF_DHIT] += 0.1F;
  			EffectEquip[ID.EF_THIT] += 0.1F;
  			EffectEquip[ID.EF_DODGE] += 10F;
  			
  	  		if (m > 5100)
  	  		{
  	  			StateFinal[ID.ATK] *= 1.1F;
  	  			StateFinal[ID.ATK_H] *= 1.1F;
  	  			StateFinal[ID.ATK_AL] *= 1.1F;
  	  			StateFinal[ID.ATK_AH] *= 1.1F;
  	  			StateFinal[ID.SPD] += 0.5F;
  	  			StateFinal[ID.MOV] += 0.1F;
  	  			StateFinal[ID.HIT] += 2F;
  	  			EffectEquip[ID.EF_DODGE] += 15F;
  	  		}
  		}
  		
  		if (m < 2101)
  		{
  			StateFinal[ID.ATK] *= 0.9F;
  			StateFinal[ID.ATK_H] *= 0.9F;
  			StateFinal[ID.ATK_AL] *= 0.9F;
  			StateFinal[ID.ATK_AH] *= 0.9F;
  			StateFinal[ID.HIT] -= 2F;
  			EffectEquip[ID.EF_DODGE] -= 10F;
  			
  	  		if (m < 901)
  	  		{
  	  			StateFinal[ID.ATK] *= 0.9F;
  	  			StateFinal[ID.ATK_H] *= 0.9F;
  	  			StateFinal[ID.ATK_AL] *= 0.9F;
  	  			StateFinal[ID.ATK_AH] *= 0.9F;
  	  			StateFinal[ID.SPD] -= 0.5F;
  	  			StateFinal[ID.MOV] -= 0.1F;
  	  			StateFinal[ID.HIT] -= 2F;
  	  			EffectEquip[ID.EF_DODGE] -= 15F;
  	  		}
  		}
  		
  	}
  	
  	/** update flare effect */
  	protected void updateSearchlight()
  	{
  		if (this.getStateMinor(ID.M.LevelSearchlight) > 0)
  		{
  			BlockPos pos = new BlockPos(this);
			float light = this.world.getLightFor(EnumSkyBlock.BLOCK, pos);

			//place new light block
  			if (light < 12F)
  			{
				BlockHelper.placeLightBlock(this.world, pos);
  			}
  			//search light block, renew lifespan
  			else
  			{
  				BlockHelper.updateNearbyLightBlock(this.world, pos);
  			}
  		}
  	}
  	
  	/** update client timer */
  	protected void updateClientTimer()
  	{
  		//attack motion timer
  		if (this.StateTimer[ID.T.AttackTime] > 0) this.StateTimer[ID.T.AttackTime]--;
  	}
  	
  	/** update server timer */
  	protected void updateServerTimer()
  	{
  		//immune timer
  		if (this.StateTimer[ID.T.ImmuneTime] > 0) this.StateTimer[ID.T.ImmuneTime]--;
  		
  		//crane change state delay
  		if (this.StateTimer[ID.T.CrandDelay] > 0) this.StateTimer[ID.T.CrandDelay]--;
  		
  		//craning timer
  		if (this.getStateMinor(ID.M.CraneState) > 1) this.StateTimer[ID.T.CraneTime]++;
  		
  		//hurt sound delay
  		if (this.StateTimer[ID.T.SoundTime] > 0) this.StateTimer[ID.T.SoundTime]--;
	
  		//emotes delay
		if (this.StateTimer[ID.T.EmoteDelay] > 0) this.StateTimer[ID.T.EmoteDelay]--;
  		
		//caress reaction time
		if (this.StateTimer[ID.T.Emotion3Time] > 0)
		{
			this.StateTimer[ID.T.Emotion3Time]--;
			
			if (this.StateTimer[ID.T.Emotion3Time] == 0)
			{
				this.setStateEmotion(ID.S.Emotion3, 0, true);
			}
		}
  	}
  	
  	/** update both side timer */
  	protected void updateBothSideTimer()
  	{
  	}
  	
  	/** update rotate */
  	protected void updateClientBodyRotate()
  	{
		float[] degree = CalcHelper.getLookDegree(posX - prevPosX, posY - prevPosY, posZ - prevPosZ, true);
		this.rotationYaw = degree[0];
  	}
  	
  	/** change ship outfit by right click cake on ship */
  	abstract public void setShipOutfit(boolean isSneaking);
  	
  	/** morale level
  	 *  0:excited, 1:happy, 2:normal, 3:tired, 4:exhausted
  	 */
  	public int getMoraleLevel()
  	{
  		int m = this.getStateMinor(ID.M.Morale);
  		
  		if (m > 5100)
  		{  //excited
			return ID.Morale.Excited;
		}
		else if (m > 3900)
		{
			return ID.Morale.Happy;
		}
		else if (m > 2100)
		{
			return ID.Morale.Normal;
		}
		else if (m > 900)
		{
			return ID.Morale.Tired;
		}
		else
		{
			return ID.Morale.Exhausted;
		}
  	}
  	
  	/** hit body part by hit height level
  	 *  height:        part:
  	 *  150~100        top
  	 *  100~80         head
  	 *  80~70          neck
  	 *  70~45          back
  	 *  45~35          belly
  	 *  35~30          ubelly
  	 *  0~30           leg
  	 */
  	protected int getHitHeightID()
  	{
  		int h = getHitHeight();
  		if (this.isSitting())
  		{
  			if (h > 60)
  			{
  	  			return ID.Body.Height.Top;
  	  		}
  	  		else if (h > 32)
  	  		{
  	  			return ID.Body.Height.Head;
  	  		}
  	  		else if (h > 30)
  	  		{
  	  			return ID.Body.Height.Neck;
  	  		}
  	  		else if (h > 15)
  	  		{
  	  			return ID.Body.Height.Chest;
  	  		}
  	  		else if (h > 12)
  	  		{
  	  			return ID.Body.Height.Belly;
  	  		}
  	  		else if (h > 10)
  	  		{
  	  			return ID.Body.Height.UBelly;
  	  		}
  	  		else
  	  		{
  	  			return ID.Body.Height.Leg;
  	  		}
  		}
  		else
  		{
  			if (h > 100)
  			{
  	  			return ID.Body.Height.Top;
  	  		}
  	  		else if (h > 76)
  	  		{
  	  			return ID.Body.Height.Head;
  	  		}
  	  		else if (h > 70)
  	  		{
  	  			return ID.Body.Height.Neck;
  	  		}
  	  		else if (h > 51)
  	  		{
  	  			return ID.Body.Height.Chest;
  	  		}
  	  		else if (h > 42)
  	  		{
  	  			return ID.Body.Height.Belly;
  	  		}
  	  		else if (h > 37)
  	  		{
  	  			return ID.Body.Height.UBelly;
  	  		}
  	  		else
  	  		{
  	  			return ID.Body.Height.Leg;
  	  		}
  		}
  	}
  	
  	/** hit body part by hit height level (angle always positive)
  	 *  angle:         part:
  	 *  
  	 *  0 ~ -70        back
  	 *  290 ~ 360
  	 *  250 ~ 290      right
  	 *  110 ~ 250      front
  	 *  70 ~ 110       left
  	 */
  	protected int getHitAngleID()
  	{
  		int a = getHitAngle();

		if (a >= 250 && a < 290)
		{  //right
  			return ID.Body.Side.Right;
		}
		else if (a >= 110 && a < 250)
		{  //front
			return ID.Body.Side.Front;
		}
		else if (a >= 70 && a < 110)
		{  //left
			return ID.Body.Side.Left;
		}
		else
		{  //back
			return ID.Body.Side.Back;
		}
  	}
  	
  	/** hit body part by hit height level
  	 * 
  	 *  (default)      back   right    front    left
  	 *  height \ angle  0     -90      -180     -270
  	 *  150~100        top     top      top      top
  	 *  100~80         head    head     face     head
  	 *  80~70          neck    neck     neck     neck
  	 *  70~45          back    arm      chest    arm
  	 *  45~35          butt    arm      belly    arm
  	 *  35~30          butt    butt     ubelly   butt
  	 *  0~30           leg     leg      leg      leg
  	 * 
  	 */
  	protected int getHitBodyID()
  	{
  		switch (getHitHeightID())
  		{
  		case ID.Body.Height.Top:
  			return ID.Body.Top;
  		case ID.Body.Height.Head:
  			if (getHitAngleID() == ID.Body.Side.Front)
  			{
  				return ID.Body.Face;
  			}
  			else
  			{
  				return ID.Body.Head;
  			}
  		case ID.Body.Height.Neck:
  			return ID.Body.Neck;
  		case ID.Body.Height.Chest:
  			switch (getHitAngleID())
  			{
  			case ID.Body.Side.Front:
  				return ID.Body.Chest;
  			case ID.Body.Side.Back:
  				return ID.Body.Back;
  			default:
  				return ID.Body.Arm;
  			}
  		case ID.Body.Height.Belly:
  			switch (getHitAngleID())
  			{
  			case ID.Body.Side.Front:
  				return ID.Body.Belly;
  			case ID.Body.Side.Back:
  				return ID.Body.Butt;
  			default:
  				return ID.Body.Arm;
  			}
  		case ID.Body.Height.UBelly:
  			if (getHitAngleID() == ID.Body.Side.Front)
  			{
  				return ID.Body.UBelly;
  			}
  			else
  			{
  				return ID.Body.Butt;
  			}
		default:  //leg
			return ID.Body.Leg;
  		}
  	}
  	
  	public void setSensitiveBody(int par1)
  	{
  		setStateMinor(ID.M.SensBody, par1);
  	}
  	
  	public int getSensitiveBody()
  	{
  		return getStateMinor(ID.M.SensBody);
  	}
  	
  	/** set random sensitive body id, ref: ID.Body
  	 *  body id: 20~30
  	 */
  	public void randomSensitiveBody()
  	{
  		int ran = this.rand.nextInt(100);
  		int bodyid = 20;
  		
  		//first roll
  		if (ran > 80)
  		{  //20%
  			bodyid = ID.Body.UBelly;
  		}
  		else if (ran > 65)
  		{  //15%
  			bodyid = ID.Body.Chest;
  		}
  		else
  		{  //55%
  			bodyid = 23 + this.rand.nextInt(8);  //roll 23~30
  		}
  		
  		//if HEAD/BACK reroll to ubelly
		if (bodyid == ID.Body.Head || bodyid == ID.Body.Back) bodyid = ID.Body.UBelly;
		if (bodyid == ID.Body.Arm || bodyid == ID.Body.Butt) bodyid = ID.Body.Chest;
  		
  		setSensitiveBody(bodyid);
  	}
  	
  	/** knockback AI target */
  	public void pushAITarget()
  	{
  		if (this.aiTarget != null)
  		{
  			//swing arm
  			this.swingArm(EnumHand.MAIN_HAND);
  			
  			//push target
  			this.aiTarget.addVelocity(-MathHelper.sin(rotationYaw * (float)Math.PI / 180.0F) * 0.5F, 
  	               0.5D, MathHelper.cos(rotationYaw * (float)Math.PI / 180.0F) * 0.5F);
  		
  			//for other player, send ship state for display
  			TargetPoint point = new TargetPoint(this.dimension, this.posX, this.posY, this.posZ, 48D);
  			CommonProxy.channelE.sendToAllAround(new S2CEntitySync(this.aiTarget, 0, S2CEntitySync.PID.SyncEntity_Motion), point);
  		}
	}
  	
  	//caress state for model display
  	protected void isCaressed()
  	{
  		//default: only top or head = caressed
  		if (getHitHeightID() <= 1)
  		{
  			setStateEmotion(ID.S.Emotion3, ID.Emotion3.CARESS, true);
  			setStateTimer(ID.T.Emotion3Time, 80);
  		}
  	}
  	
  	/** normal emotes */
  	protected void reactionNormal()
  	{
  		Random ran = new Random();
  		int m = getStateMinor(ID.M.Morale);
  		int body = getHitBodyID();
  		int baseMorale = (int) ((float)ConfigHandler.baseCaressMorale * 2.5F);
  		LogHelper.info("DEBUG : hit ship: Morale: "+m+" BodyID: "+body+" sensitiveBodyID: "+this.getSensitiveBody()); 		
  		
  		//change emotion3 to caressed
  		isCaressed();
  		
  		//show emotes by morale level
		switch (getMoraleLevel())
		{
		case 0:   //excited
			//check sensitive body
	  		if (body == getSensitiveBody())
	  		{
	  			if (this.rand.nextInt(2) == 0)
	  			{
	  				applyParticleEmotion(31);  //shy
	  			}
	  			else
	  			{
	  				applyParticleEmotion(10);  //dizzy
	  			}
	  			
	  			if (getStateMinor(ID.M.Morale) < 8100)
	  			{
	  				this.setStateMinor(ID.M.Morale, m + baseMorale * 3 + this.rand.nextInt(baseMorale + 1));
	  			}
	  		}
	  		//other reaction
	  		else
	  		{
				switch (body)
				{
				case ID.Body.UBelly:
				case ID.Body.Butt:
				case ID.Body.Chest:
				case ID.Body.Face:
					if (this.getStateFlag(ID.F.IsMarried))
					{
						applyParticleEmotion(15);  //kiss
					}
					else
					{
						applyParticleEmotion(1);  //heart
					}
					break;
				default:
					if (this.rand.nextInt(2) == 0)
					{
						applyParticleEmotion(1);  //heart
					}
					else
					{
						applyParticleEmotion(7);  //note
					}
					break;
				}
	  		}
			break;
		case 1:   //happy
			//check sensitive body
	  		if (body == getSensitiveBody())
	  		{
	  			if (this.getStateFlag(ID.F.IsMarried))
	  			{
	  				if (this.rand.nextInt(2) == 0)
	  				{
		  				applyParticleEmotion(31);  //shy
		  			}
		  			else
		  			{
		  				applyParticleEmotion(10);  //dizzy
		  			}
				}
				else
				{
					applyParticleEmotion(10);  //dizzy
				}
	  			
	  			setStateMinor(ID.M.Morale, m + baseMorale + this.rand.nextInt(baseMorale + 1));
	  		}
	  		//other reaction
	  		else
	  		{
	  			switch (body)
	  			{
				case ID.Body.UBelly:
				case ID.Body.Butt:
				case ID.Body.Chest:
				case ID.Body.Face:
					if (this.getStateFlag(ID.F.IsMarried))
					{
						applyParticleEmotion(1);  //heart
					}
					else
					{
						applyParticleEmotion(16);  //haha
					}
					break;
				default:
					if (this.rand.nextInt(2) == 0)
					{
						applyParticleEmotion(1);  //heart
					}
					else
					{
						applyParticleEmotion(7);  //note
					}
					break;
				}
	  		}
			break;
		case 2:   //normal
			//check sensitive body
	  		if (body == getSensitiveBody())
	  		{
	  			if (this.getStateFlag(ID.F.IsMarried))
	  			{
	  				applyParticleEmotion(19);  //lick
				}
				else
				{
					applyParticleEmotion(18);  //sigh
				}
	  			
	  			setStateMinor(ID.M.Morale, m + baseMorale + this.rand.nextInt(baseMorale + 1));
	  			
	  			//push target
	  			if (ran.nextInt(6) == 0)
	  			{
	  				this.pushAITarget();
	  				this.playSound(this.getCustomSound(5, this), this.getSoundVolume(), 1F / (this.getRNG().nextFloat() * 0.2F + 0.9F));
	  			}
	  		}
	  		//other reaction
	  		else
	  		{
	  			switch (body)
	  			{
				case ID.Body.UBelly:
				case ID.Body.Butt:
				case ID.Body.Chest:
				case ID.Body.Face:
					if (this.getStateFlag(ID.F.IsMarried))
					{
						applyParticleEmotion(1);  //heart
					}
					else
					{
						applyParticleEmotion(27);  //-w-
					}
					
					//push target
		  			if (ran.nextInt(8) == 0)
		  			{
		  				this.pushAITarget();
		  				this.playSound(this.getCustomSound(5, this), this.getSoundVolume(), 1F / (this.getRNG().nextFloat() * 0.2F + 0.9F));
		  			}
					break;
				default:
					switch (this.rand.nextInt(7))
					{
					case 1:
						applyParticleEmotion(30);  //pif
						break;
					case 3:
						applyParticleEmotion(7);  //note
						break;
					case 4:
						applyParticleEmotion(26);  //ya
						break;
					case 6:
						applyParticleEmotion(11);  //find
						break;
					default:
						applyParticleEmotion(29);  //blink
						break;
					}
					break;
				}
	  		}
			break;
		case 3:   //tired
			//check sensitive body
	  		if  (body == getSensitiveBody())
	  		{
	  			setStateEmotion(ID.S.Emotion, ID.Emotion.O_O, true);
	  			applyParticleEmotion(32);  //hmm
	  			setStateMinor(ID.M.Morale, m + this.rand.nextInt(baseMorale + 1));
	  			
	  			//push target
	  			if (ran.nextInt(2) == 0)
	  			{
	  				this.pushAITarget();
	  				this.playSound(this.getCustomSound(5, this), this.getSoundVolume(), 1F / (this.getRNG().nextFloat() * 0.2F + 0.9F));
	  			}
	  			else if (this.aiTarget != null && ran.nextInt(8) == 0)
	  			{
	  				switch (ran.nextInt(3))
	  				{
			    	case 0:
			    		attackEntityWithAmmo(this.aiTarget);
			    		break;
			    	case 1:
			    		attackEntityWithHeavyAmmo(this.aiTarget);
			    		break;
			    	default:
			    		attackEntityAsMob(this.aiTarget);
			    		break;
			    	}
	  			}
	  		}
	  		//other reaction
	  		else
	  		{
	  			switch (body)
	  			{
				case ID.Body.UBelly:
				case ID.Body.Butt:
				case ID.Body.Chest:
				case ID.Body.Face:
					setStateEmotion(ID.S.Emotion, ID.Emotion.O_O, true);
					applyParticleEmotion(32);  //hmm
					//push target
		  			if (ran.nextInt(4) == 0)
		  			{
		  				this.pushAITarget();
		  				this.playSound(this.getCustomSound(5, this), this.getSoundVolume(), 1F / (this.getRNG().nextFloat() * 0.2F + 0.9F));
		  			}
					break;
				default:
					switch (this.rand.nextInt(5))
					{
					case 1:
						applyParticleEmotion(30);  //pif
						break;
					case 2:
						applyParticleEmotion(2);  //panic
						break;
					case 4:
						applyParticleEmotion(3);  //?
						break;
					default:
						applyParticleEmotion(0);  //sweat
						break;
					}
					break;
				}
	  		}
			break;
		default:  //exhausted
			//check sensitive body
	  		if (body == getSensitiveBody())
	  		{
	  			setStateEmotion(ID.S.Emotion, ID.Emotion.O_O, true);
	  			applyParticleEmotion(6);  //angry
	  			setStateMinor(ID.M.Morale, m - baseMorale * 10 - this.rand.nextInt(baseMorale * 5 + 1));
	  			
	  			//push target
  				this.pushAITarget();
  				this.playSound(this.getCustomSound(5, this), this.getSoundVolume(), 1F / (this.getRNG().nextFloat() * 0.2F + 0.9F));
			    
			    if (this.aiTarget != null && ran.nextInt(3) == 0)
			    {
			    	switch (ran.nextInt(3))
			    	{
			    	case 0:
			    		attackEntityWithAmmo(this.aiTarget);
			    		break;
			    	case 1:
			    		attackEntityWithHeavyAmmo(this.aiTarget);
			    		break;
			    	default:
			    		attackEntityAsMob(this.aiTarget);
			    		break;
			    	}
	  			}
	  		}
	  		//other reaction
	  		else
	  		{
	  			switch (body)
	  			{
				case ID.Body.UBelly:
				case ID.Body.Butt:
				case ID.Body.Chest:
				case ID.Body.Face:
					setStateEmotion(ID.S.Emotion, ID.Emotion.T_T, true);
					
					if (this.rand.nextInt(3) == 0)
					{
						applyParticleEmotion(6);  //angry
					}
					else
					{
						applyParticleEmotion(32);  //hmm
					}
					
					//push target
		  			if (ran.nextInt(2) == 0)
		  			{
		  				this.pushAITarget();
		  				this.playSound(this.getCustomSound(5, this), this.getSoundVolume(), 1F / (this.getRNG().nextFloat() * 0.2F + 0.9F));
		  			}
		  			else if (this.aiTarget != null && ran.nextInt(5) == 0)
		  			{
		  				switch (ran.nextInt(3))
		  				{
				    	case 0:
				    		attackEntityWithAmmo(this.aiTarget);
				    		break;
				    	case 1:
				    		attackEntityWithHeavyAmmo(this.aiTarget);
				    		break;
				    	default:
				    		attackEntityAsMob(this.aiTarget);
				    		break;
				    	}
		  			}
					break;
				default:
					switch (this.rand.nextInt(5))
					{
					case 1:
						applyParticleEmotion(8);  //cry
						break;
					case 2:
						applyParticleEmotion(2);  //panic
						break;
					case 3:
						applyParticleEmotion(20);  //orz
						break;
					case 4:
						applyParticleEmotion(5);  //...
						break;
					default:
						applyParticleEmotion(34);  //lll
						break;
					}
					break;
				}
	  		}
			break;
		}//end morale level switch
  	}
  	
  	/** stranger (not owner) emotes */
  	protected void reactionStranger()
  	{
  		int body = getHitBodyID();
  		LogHelper.info("DEBUG : hit ship: BodyID: "+body+" sensitiveBodyID: "+this.getSensitiveBody()); 		

		//check sensitive body
  		if (body == getSensitiveBody())
  		{
  			if (this.rand.nextInt(2) == 0)
  			{
				applyParticleEmotion(6);  //angry
			}
			else
			{
				applyParticleEmotion(22);  //x
			}
  			
  			//push target
  			if (rand.nextInt(2) == 0)
  			{
  				this.pushAITarget();
  				this.playSound(this.getCustomSound(5, this), this.getSoundVolume(), 1F / (this.getRNG().nextFloat() * 0.2F + 0.9F));
  			}
  			else if (this.aiTarget != null && rand.nextInt(4) == 0)
  			{
  				switch (rand.nextInt(3))
  				{
		    	case 0:
		    		attackEntityWithAmmo(this.aiTarget);
		    		break;
		    	case 1:
		    		attackEntityWithHeavyAmmo(this.aiTarget);
		    		break;
		    	default:
		    		attackEntityAsMob(this.aiTarget);
		    		break;
		    	}
  			}
  		}
  		//other reaction
  		else
  		{
  			switch (body)
  			{
			case ID.Body.UBelly:
			case ID.Body.Butt:
			case ID.Body.Chest:
			case ID.Body.Face:
				setStateEmotion(ID.S.Emotion, ID.Emotion.O_O, true);
				
				if (this.rand.nextInt(2) == 0)
				{
					applyParticleEmotion(6);  //angry
				}
				else
				{
					applyParticleEmotion(5);  //...
				}
				
				//push target
	  			if (rand.nextInt(4) == 0)
	  			{
	  				this.pushAITarget();
	  				this.playSound(this.getCustomSound(5, this), this.getSoundVolume(), 1F / (this.getRNG().nextFloat() * 0.2F + 0.9F));
	  			}
	  			else if (this.aiTarget != null && rand.nextInt(8) == 0)
	  			{
	  				switch (rand.nextInt(3))
	  				{
			    	case 0:
			    		attackEntityWithAmmo(this.aiTarget);
			    		break;
			    	case 1:
			    		attackEntityWithHeavyAmmo(this.aiTarget);
			    		break;
			    	default:
			    		attackEntityAsMob(this.aiTarget);
			    		break;
			    	}
	  			}
				break;
			default:
				switch (this.rand.nextInt(7))
				{
				case 1:
					applyParticleEmotion(9);  //hungry
					break;
				case 2:
					applyParticleEmotion(2);  //panic
					break;
				case 3:
					applyParticleEmotion(20);  //orz
					break;
				case 4:
					applyParticleEmotion(8);  //cry
					break;
				case 5:
					applyParticleEmotion(0);  //sweat
					break;
				default:
					applyParticleEmotion(34);  //lll
					break;
				}
				break;
			}
  		}
  	}
  	
  	/** damaged emotes */
  	protected void reactionAttack()
  	{
  		//show emotes by morale level
		switch (getMoraleLevel())
		{
		case 0:   //excited
			switch (this.rand.nextInt(8))
			{
			case 1:
				applyParticleEmotion(33);  //:p
				break;
			case 2:
				applyParticleEmotion(17);  //gg
				break;
			case 3:
				applyParticleEmotion(19);  //lick
				break;
			case 4:
				applyParticleEmotion(16);  //ha
				break;
			default:
				applyParticleEmotion(7);  //note
				break;
			}
			break;
		case 1:   //happy
		case 2:   //normal
		case 3:   //tired
		default:  //exhausted
			switch (this.rand.nextInt(8))
			{
			case 1:
				applyParticleEmotion(14);  //+_+
				break;
			case 2:
				applyParticleEmotion(30);  //pif
				break;
			case 3:
				applyParticleEmotion(7);  //note
				break;
			case 4:
				applyParticleEmotion(4);  //!
				break;
			case 5:
				applyParticleEmotion(7);  //note
				break;
			default:
				applyParticleEmotion(6);  //angry
				break;
			}
			break;
		}//end morale level switch
  	}
  	
  	/** damaged emotes */
  	protected void reactionDamaged()
  	{
  		int body = getHitBodyID();
  		
  		//show emotes by morale level
		switch (getMoraleLevel())
		{
		case 0:   //excited
		case 1:   //happy
		case 2:   //normal
			//check sensitive body
	  		if (body == getSensitiveBody())
	  		{
	  			applyParticleEmotion(6);  //angry
	  		}
	  		//other reaction
	  		else
	  		{
				switch (body)
				{
				case ID.Body.UBelly:
				case ID.Body.Butt:
				case ID.Body.Chest:
				case ID.Body.Face:
					applyParticleEmotion(6);  //angry
					break;
				default:
					switch (this.rand.nextInt(7))
					{
					case 1:
						applyParticleEmotion(30);  //pif
						break;
					case 2:
						applyParticleEmotion(5);  //...
						break;
					case 3:
						applyParticleEmotion(2);  //panic
						break;
					case 4:
						applyParticleEmotion(3);  //?
						break;
					default:
						applyParticleEmotion(8);  //cry
						break;
					}
					break;
				}
	  		}
			break;
		case 3:   //tired
		default:  //exhausted
			//check sensitive body
	  		if (body == getSensitiveBody())
	  		{
	  			applyParticleEmotion(10);  //dizzy
	  		}
	  		//other reaction
	  		else
	  		{
	  			switch (body)
	  			{
				case ID.Body.UBelly:
				case ID.Body.Butt:
				case ID.Body.Chest:
				case ID.Body.Face:
					applyParticleEmotion(10);  //dizzy
					break;
				default:
					switch (this.rand.nextInt(7))
					{
					case 1:
						applyParticleEmotion(30);  //pif
						break;
					case 2:
						applyParticleEmotion(5);  //...
						break;
					case 3:
						applyParticleEmotion(2);  //panic
						break;
					case 4:
						applyParticleEmotion(3);  //?
						break;
					case 5:
						applyParticleEmotion(0);  //sweat
						break;
					default:
						applyParticleEmotion(8);  //cry
						break;
					}
				}
	  		}
			break;
		}//end morale level switch
  	}
  	
  	/** idle emotes */
  	protected void reactionIdle()
  	{
  		//show emotes by morale level
		switch (getMoraleLevel())
		{
		case 0:   //excited
		case 1:   //happy
			if (this.getStateFlag(ID.F.IsMarried) && this.rand.nextInt(2) == 0)
			{
				switch (this.rand.nextInt(3))
				{
				case 1:
					applyParticleEmotion(31);  //shy
					break;
				default:
					applyParticleEmotion(15);  //kiss
					break;
				}
				
				return;
			}
			
			switch (this.rand.nextInt(10))
			{
			case 1:
				applyParticleEmotion(33);  //:p
				break;
			case 2:
				applyParticleEmotion(17);  //gg
				break;
			case 3:
				applyParticleEmotion(19);  //lick
				break;
			case 4:
				applyParticleEmotion(9);  //hungry
				break;
			case 5:
				applyParticleEmotion(1);  //love
				break;
			case 6:
				applyParticleEmotion(15);  //kiss
				break;
			case 7:
				applyParticleEmotion(16);  //haha
				break;
			case 8:
				applyParticleEmotion(14);  //+_+
				break;
			default:
				applyParticleEmotion(7);  //note
				break;
			}
			break;
		case 2:   //normal
			if (this.getStateFlag(ID.F.IsMarried) && this.rand.nextInt(2) == 0)
			{
				switch (this.rand.nextInt(3))
				{
				case 1:
					applyParticleEmotion(1);  //love
					break;
				default:
					applyParticleEmotion(15);  //kiss
					break;
				}
				
				return;
			}
			
			switch (this.rand.nextInt(8))
			{
			case 1:
				applyParticleEmotion(11);  //find
				break;
			case 2:
				applyParticleEmotion(3);  //?
				break;
			case 3:
				applyParticleEmotion(13);  //nod
				break;
			case 4:
				applyParticleEmotion(9);  //hungry
				break;
			case 5:
				applyParticleEmotion(18);  //sigh
				break;
			case 7:
				applyParticleEmotion(16);  //haha
				break;
			default:
				applyParticleEmotion(29);  //blink
				break;
			}
			break;
		case 3:   //tired
		default:  //exhausted
			switch (this.rand.nextInt(8))
			{
			case 1:
				applyParticleEmotion(0);  //drop
				break;
			case 2:
				applyParticleEmotion(2);  //panic
				break;
			case 3:
				applyParticleEmotion(3);  //?
				break;
			case 4:
				applyParticleEmotion(8);  //cry
				break;
			case 5:
				applyParticleEmotion(10);  //dizzy
				break;
			case 6:
				applyParticleEmotion(20);  //orz
				break;
			default:
				applyParticleEmotion(32);  //hmm
				break;
			}
			break;
		}//end morale level switch
  	}
  	
  	/** command emotes */
  	protected void reactionCommand()
  	{
  		//show emotes by morale level
		switch (getMoraleLevel())
		{
		case 0:   //excited
		case 1:   //happy
		case 2:   //normal
			switch (this.rand.nextInt(7))
			{
			case 1:
				applyParticleEmotion(21);  //o
				break;
			case 2:
				applyParticleEmotion(4);  //!
				break;
			case 3:
				applyParticleEmotion(14);  //+_+
				break;
			case 4:
				applyParticleEmotion(11);  //find
				break;
			default:
				applyParticleEmotion(13);  //nod
				break;
			}
			break;
		case 3:   //tired
		default:  //exhausted
			switch (this.rand.nextInt(8))
			{
			case 1:
				applyParticleEmotion(0);  //drop
			case 2:
				applyParticleEmotion(33);  //:p
				break;
			case 3:
				applyParticleEmotion(3);  //?
				break;
			case 5:
				applyParticleEmotion(10);  //dizzy
				break;
			case 6:
				applyParticleEmotion(13);  //nod
				break;
			default:
				applyParticleEmotion(32);  //hmm
				break;
			}
			break;
		}//end morale level switch
  	}
  	
  	/** shock emotes */
  	protected void reactionShock()
  	{
		switch (this.rand.nextInt(8))
		{
		case 1:
			applyParticleEmotion(0);  //drop
			break;
		case 2:
			applyParticleEmotion(8);  //cry
			break;
		case 3:
			applyParticleEmotion(4);  //!
			break;
		default:
			applyParticleEmotion(12);  //omg
			break;
		}
  	}
  	
  	/** emotes method
  	 * 
  	 *  type:
  	 *  0: caress head (owner)
  	 *  1: caress head (other)
  	 *  2: damaged
  	 *  3: attack
  	 *  4: idle
  	 *  5: command
  	 *  6: shock
  	 */
  	public void applyEmotesReaction(int type)
  	{
  		Random ran = new Random();
  		
  		switch (type)
  		{
  		case 1:  //caress head (no fuel / not owner)
  			if (ran.nextInt(9) == 0 && this.getEmotesTick() <= 0)
  			{
				this.setEmotesTick(60);
				reactionStranger();
			}
  			break;
  		case 2:  //damaged
  			if (this.getEmotesTick() <= 10)
  			{
				this.setEmotesTick(40);
				reactionDamaged();
			}
  			break;
  		case 3:  //attack
  			if (ran.nextInt(6) == 0 && this.getEmotesTick() <= 0)
  			{
				this.setEmotesTick(60);
				reactionAttack();
			}
  			break;
  		case 4:
  			if (ran.nextInt(3) == 0 && this.getEmotesTick() <= 0)
  			{
				this.setEmotesTick(20);
				reactionIdle();
			}
  			break;
  		case 5:
  			if (ran.nextInt(3) == 0 && this.getEmotesTick() <= 0)
  			{
				this.setEmotesTick(25);
				reactionCommand();
			}
  			break;
  		case 6:
			reactionShock();
  			break;
  		default: //caress head (owner)
  			if (ran.nextInt(7) == 0 && this.getEmotesTick() <= 0)
  			{
				this.setEmotesTick(50);
				reactionNormal();
			}
  			break;
  		}
  	}
  	
  	/** special particle at entity
  	 * 
  	 *  type: 0:miss, 1:critical, 2:double hit, 3:triple hit
  	 */
  	protected void applyParticleSpecialEffect(int type)
  	{
  		TargetPoint point = new TargetPoint(this.dimension, this.posX, this.posY, this.posZ, 64D);
  		
  		switch (type)
  		{
  		case 1:  //critical
      		CommonProxy.channelP.sendToAllAround(new S2CSpawnParticle(this, 11, false), point);
  			break;
  		case 2:  //double hit
      		CommonProxy.channelP.sendToAllAround(new S2CSpawnParticle(this, 12, false), point);
  			break;
  		case 3:  //triple hit
      		CommonProxy.channelP.sendToAllAround(new S2CSpawnParticle(this, 13, false), point);
  			break;
		default: //miss
      		CommonProxy.channelP.sendToAllAround(new S2CSpawnParticle(this, 10, false), point);
			break;
  		}
  	}
  	
  	/** spawn emotion particle */
  	public void applyParticleEmotion(int type)
  	{
  		float h = isSitting() ? this.height * 0.4F : this.height * 0.45F;
  		
  		//server side emotes
  		if (!this.world.isRemote)
  		{
  			TargetPoint point = new TargetPoint(this.dimension, this.posX, this.posY, this.posZ, 64D);
  	      	CommonProxy.channelP.sendToAllAround(new S2CSpawnParticle(this, 36, h, 0, type), point);
  		}
  		//client side emotes
  		else
  		{
  			ParticleHelper.spawnAttackParticleAtEntity(this, h, 0, type, (byte)36);
  		}
  	}
  	
  	/** attack particle at attacker
  	 * 
  	 *  type: 0:melee, 1:light cannon, 2:heavy cannon, 3:light air, 4:heavy air
  	 *  vec: 0:distX, 1:distY, 2:distZ, 3:dist sqrt
  	 */
  	public void applyParticleAtAttacker(int type, Entity target, float[] vec)
  	{
  		TargetPoint point = new TargetPoint(this.dimension, this.posX, this.posY, this.posZ, 64D);
        
  		switch (type)
  		{
  		case 1:  //light cannon
  			CommonProxy.channelP.sendToAllAround(new S2CSpawnParticle(this, 6, this.posX, this.posY, this.posZ, vec[0], vec[1], vec[2], true), point);
  			break;
  		case 2:  //heavy cannon
  			CommonProxy.channelP.sendToAllAround(new S2CSpawnParticle(this, 0, true), point);
  			break;
  		case 3:  //light aircraft
  			CommonProxy.channelP.sendToAllAround(new S2CSpawnParticle(this, 0, true), point);
  			break;
  		case 4:  //heavy aircraft
  			CommonProxy.channelP.sendToAllAround(new S2CSpawnParticle(this, 0, true), point);
  			break;
		default: //melee
			CommonProxy.channelP.sendToAllAround(new S2CSpawnParticle(this, 0, true), point);
			break;
  		}
  	}
  	
  	/** attack particle at target
  	 * 
  	 *  type: 0:melee, 1:light cannon, 2:heavy cannon, 3:light air, 4:heavy air
  	 *  vec: 0:distX, 1:distY, 2:distZ, 3:dist sqrt
  	 */
  	public void applyParticleAtTarget(int type, Entity target, float[] vec)
  	{
  		TargetPoint point = new TargetPoint(this.dimension, this.posX, this.posY, this.posZ, 64D);
  		
  		switch (type)
  		{
  		case 1:  //light cannon
			CommonProxy.channelP.sendToAllAround(new S2CSpawnParticle(target, 9, false), point);
  		break;
  		case 2:  //heavy cannon
  		break;
  		case 3:  //light aircraft
  		break;
  		case 4:  //heavy aircraft
  		break;
		default: //melee
    		CommonProxy.channelP.sendToAllAround(new S2CSpawnParticle(target, 1, false), point);
		break;
  		}
  	}
  	
  	/** attack particle at attacker
  	 * 
  	 *  type: 0:melee, 1:light cannon, 2:heavy cannon, 3:light air, 4:heavy air
  	 */
  	public void applySoundAtAttacker(int type, Entity target)
  	{
  		switch (type)
  		{
  		case 1:  //light cannon
  			this.playSound(ModSounds.SHIP_FIRELIGHT, ConfigHandler.volumeFire, this.getSoundPitch() * 0.85F);
  	        
  			//entity sound
  			if (this.rand.nextInt(10) > 7)
  			{
  				this.playSound(this.getCustomSound(1, this), this.getSoundVolume(), this.getSoundPitch());
  	        }
  		break;
  		case 2:  //heavy cannon
  			this.playSound(ModSounds.SHIP_FIREHEAVY, ConfigHandler.volumeFire, this.getSoundPitch() * 0.85F);
  	        
  	        //entity sound
  	        if (this.getRNG().nextInt(10) > 7)
  	        {
  	        	this.playSound(this.getCustomSound(1, this), this.getSoundVolume(), this.getSoundPitch());
  	        }
  		break;
  		case 3:  //light aircraft
  		case 4:  //heavy aircraft
  			this.playSound(ModSounds.SHIP_AIRCRAFT, ConfigHandler.volumeFire * 0.5F, this.getSoundPitch() * 0.85F);
  	  	break;
		default: //melee
			if (this.getRNG().nextInt(2) == 0)
			{
				this.playSound(this.getCustomSound(1, this), this.getSoundVolume(), this.getSoundPitch());
	        }
		break;
  		}//end switch
  	}
  	
  	/** attack particle at target
  	 * 
  	 *  type: 0:melee, 1:light cannon, 2:heavy cannon, 3:light air, 4:heavy air
  	 */
  	public void applySoundAtTarget(int type, Entity target)
  	{
  		switch (type)
  		{
  		case 1:  //light cannon
  		break;
  		case 2:  //heavy cannon
  		break;
  		case 3:  //light aircraft
  		break;
  		case 4:  //heavy aircraft
  		break;
		default: //melee
		break;
  		}
  	}
  	
  	/** attack base damage
  	 * 
  	 *  type: 0:melee, 1:light cannon, 2:heavy cannon, 3:light air, 4:heavy air
  	 */
  	public float getAttackBaseDamage(int type, Entity target)
  	{
  		switch (type)
  		{
  		case 1:  //light cannon
  			return CalcHelper.calcDamageBySpecialEffect(this, target, StateFinal[ID.ATK], 0);
  		case 2:  //heavy cannon
  			return StateFinal[ID.ATK_H];
  		case 3:  //light aircraft
  			return StateFinal[ID.ATK_AL];
  		case 4:  //heavy aircraft
  			return StateFinal[ID.ATK_AH];
		default: //melee
			return StateFinal[ID.ATK] * 0.125F;
  		}
  	}
  	
  	//get # inv pages ship have
  	public int getInventoryPageSize()
  	{
  		return this.StateMinor[ID.M.InvSize];
  	}
  	
  	//set # inv pages ship have
  	public void setInventoryPageSize(int par1)
  	{
  		this.StateMinor[ID.M.InvSize] = par1;
  	}
  	
  	/** set flare on target */
  	public void flareTarget(Entity target)
  	{
  		//server side, send flare packet
  		if (!this.world.isRemote)
  		{
  	  		if (this.getStateMinor(ID.M.LevelFlare) > 0 && target != null)
  	  		{
  	  			BlockPos pos = new BlockPos(target);
				TargetPoint point = new TargetPoint(this.dimension, this.posX, this.posY, this.posZ, 64D);
				CommonProxy.channelG.sendToAllAround(new S2CInputPackets(S2CInputPackets.PID.FlareEffect, pos.getX(), pos.getY(), pos.getZ()), point);
  	  		}
  		}
  	}
  	
  	/** release ticket when dead */
  	@Override
  	public void setDead()
  	{
  		//clear chunk loader
  		this.clearChunkLoader();
  		
  		super.setDead();
    }

  	
  	/** release chunk loader ticket */ //TODO need review
  	public void clearChunkLoader()
  	{
  		//unforce chunk
  		if (this.chunks != null)
  		{
  	  		for(ChunkPos p : this.chunks)
  	  		{
				ForgeChunkManager.unforceChunk(this.chunkTicket, p);
			}
  		}

  		//release ticket
  		if (this.chunkTicket != null)
  		{
  	  		ForgeChunkManager.releaseTicket(this.chunkTicket);
  		}
  		
  		this.chunks = null;
  		this.chunkTicket = null;
  	}
  	
  	/** chunk loader ticking */
  	public void updateChunkLoader()
  	{
  		if (!this.world.isRemote)
  		{
  			//set ticket
  	  		setChunkLoader();
  	  		
  	  		//apply ticket
  	  		applyChunkLoader();
  		}
  	}
  	
  	/** request chunk loader ticket
  	 * 
  	 *  player must be ONLINE
  	 */
  	private void setChunkLoader()
  	{
  		//if equip chunk loader
  		if (this.getStateMinor(ID.M.LevelChunkLoader) > 0)
  		{
  			EntityPlayer player = null;
  			int uid = this.getPlayerUID();
  			
  			//check owner exists
  			if (uid > 0)
  			{
  				player = EntityHelper.getEntityPlayerByUID(uid);
  				
  				//player is online and no ticket
  				if (player != null && this.chunkTicket == null)
  				{
					//get ticket
			  		this.chunkTicket = ForgeChunkManager.requestPlayerTicket(ShinColle.instance, player.getDisplayNameString(), world, ForgeChunkManager.Type.ENTITY);
  				
			  		if (this.chunkTicket != null)
			  		{
			  			this.chunkTicket.bindEntity(this);
			  		}
			  		else
			  		{
			  			LogHelper.info("INFO : Ship get chunk loader ticket fail.");
			  			clearChunkLoader();
			  		}
  				}
  			}//end check player
  		}//end can chunk loader
  		else
  		{
  			clearChunkLoader();
  		}
  	}
  	
  	/** force chunk load */
  	private void applyChunkLoader()
  	{
  		if (this.chunkTicket != null)
  		{
  			HashSet<ChunkPos> unloadChunks = new HashSet<ChunkPos>();
  			HashSet<ChunkPos> loadChunks = null;
  			HashSet<ChunkPos> tempChunks = new HashSet<ChunkPos>();
  			
  			//get chunk x,z
  			int cx = MathHelper.floor(this.posX) >> 4;
			int cz = MathHelper.floor(this.posZ) >> 4;
			
  			//get new chunk
			loadChunks = BlockHelper.getChunksWithinRange(world, cx, cz, ConfigHandler.chunkloaderMode);
  			
			//get unload chunk
			if (this.chunks != null)
			{
				unloadChunks.addAll(this.chunks);	//copy old chunks
			}
			unloadChunks.removeAll(loadChunks);		//remove all load chunks
			
			//get load chunk
			tempChunks.addAll(loadChunks);			//copy new chunks
			if (this.chunks != null)
			{
				loadChunks.removeAll(this.chunks);	//remove all old chunks
			}
			
			//set old chunk
			this.chunks = tempChunks;
			
  			//unforce unload chunk
			for(ChunkPos p : unloadChunks)
			{
				ForgeChunkManager.unforceChunk(this.chunkTicket, p);
			}
			
			//force load chunk
			for(ChunkPos p : loadChunks)
			{
				ForgeChunkManager.forceChunk(this.chunkTicket, p);
			}
			
			LogHelper.debug("DEBUG : ship chunk loader: "+this.chunks+" "+this);
        }
  	}
  	
  	//get last waypoint, for waypoint loop checking
  	@Override
  	public BlockPos getLastWaypoint()
  	{
  		return this.waypoints[0];
  	}
  	
  	@Override
  	public void setLastWaypoint(BlockPos pos)
  	{
  		this.waypoints[0] = pos;
  	}
  	
  	//convert wp stay time to ticks
  	public static int wpStayTime2Ticks(int wpstay)
  	{
  		switch (wpstay)
  		{
  		case 1:
  		case 2:
  		case 3:
  		case 4:
  		case 5:
  			return wpstay * 100;
  		case 6:
  		case 7:
  		case 8:
  		case 9:
  		case 10:
  			return (wpstay - 5) * 1200;
  		case 11:
  		case 12:
  		case 13:
  		case 14:
  		case 15:
  		case 16:
  			return (wpstay - 10) * 12000;
		default:
			return 0;
  		}
  	}

	@Override
	public int getWpStayTime()
	{
		return getStateTimer(ID.T.WpStayTime);
	}

	@Override
	public int getWpStayTimeMax()
	{
		return wpStayTime2Ticks(getStateMinor(ID.M.WpStay));
	}

	@Override
	public void setWpStayTime(int time)
	{
		setStateTimer(ID.T.WpStayTime, time);
	}
	
	@Override
	protected void collideWithNearbyEntities()
    {
        List<Entity> list = this.world.getEntitiesWithinAABBExcludingEntity(this, this.getEntityBoundingBox().expand(0.2D, 0D, 0.2D));

        if (list != null)
        {
            for (Entity ent : list)
            {
                if (ent.canBePushed())
                {
                	//no colli with airplane or mount
                	if (!(ent instanceof BasicEntityAirplane || ent.equals(this.getRidingEntity())))
                	{
                		this.collideWithEntity(ent);
                	}
                	//no colli with all passenger
                	else
                	{
                		for (Entity p : this.getPassengers())
                		{
                			if (!p.equals(ent))
                			{
                				this.collideWithEntity(ent);
                			}
                		}
                	}
                }
            }
        }
    }
	
	@Override
    public int getPortalCooldown()
    {
        return 40;
    }
	
  	/**
  	 * get/setField為GUI container更新用
  	 * 使資料可以只用相同方法取值, 不用每個資料用各自方法取值
  	 * 方便for loop撰寫
  	 * 
  	 * field id:
  	 * 0: ExpCurrent, 1:NumAmmoLight, 2:NumAmmoHeavy, 3:NumAirLight, 4:NumAirHeavy
  	 * 5: UseMelee, 6:UseAmmoLight, 7:UseAmmoHeavy, 8:UseAirLight, 9:UseAirHeavy
  	 * 10:IsMarried, 11:FollowMin, 12:FollowMax, 13:FleeHP, 14:PassiveAI
  	 * 15:UseRingEffect, 16:OnSightChase, 17:PVPFirst, 18:AntiAir, 19:AntiSS
  	 * 20:TimeKeeper, 21:Morale, 22:InvSize, 23:PickItem, 24:WpStay
  	 * 25:Kills, 26:NumGrudge, 27:ShowInvPage
  	 */
	public int getFieldCount()
	{
		return 28;
	}
	
	public int getField(int id)
	{
		switch (id)
		{
		case 0:
			return this.StateMinor[ID.M.ExpCurrent];
		case 1:
			return this.StateMinor[ID.M.NumAmmoLight];
		case 2:
			return this.StateMinor[ID.M.NumAmmoHeavy];
		case 3:
			return this.StateMinor[ID.M.NumAirLight];
		case 4:
			return this.StateMinor[ID.M.NumAirHeavy];
		case 5:
			return this.getStateFlagI(ID.F.UseMelee);
		case 6:
			return this.getStateFlagI(ID.F.UseAmmoLight);
		case 7:
			return this.getStateFlagI(ID.F.UseAmmoHeavy);
		case 8:
			return this.getStateFlagI(ID.F.UseAirLight);
		case 9:
			return this.getStateFlagI(ID.F.UseAirHeavy);
		case 10:
			return this.getStateFlagI(ID.F.IsMarried);
		case 11:
			return this.StateMinor[ID.M.FollowMin];
		case 12:
			return this.StateMinor[ID.M.FollowMax];
		case 13:
			return this.StateMinor[ID.M.FleeHP];
		case 14:
			return this.getStateFlagI(ID.F.PassiveAI);
		case 15:
			return this.getStateFlagI(ID.F.UseRingEffect);
		case 16:
			return this.getStateFlagI(ID.F.OnSightChase);
		case 17:
			return this.getStateFlagI(ID.F.PVPFirst);
		case 18:
			return this.getStateFlagI(ID.F.AntiAir);
		case 19:
			return this.getStateFlagI(ID.F.AntiSS);
		case 20:
			return this.getStateFlagI(ID.F.TimeKeeper);
		case 21:
			return this.StateMinor[ID.M.Morale];
		case 22:
			return this.StateMinor[ID.M.InvSize];
		case 23:
			return this.getStateFlagI(ID.F.PickItem);
		case 24:
			return this.StateMinor[ID.M.WpStay];
		case 25:
			return this.StateMinor[ID.M.Kills];
		case 26:
			return this.StateMinor[ID.M.NumGrudge];
		case 27:
			return this.itemHandler.getInventoryPage();
		}
		
		return 0;
	}

	public void setField(int id, int value)
	{
		switch (id)
		{
		case 0:
			this.StateMinor[ID.M.ExpCurrent] = value;
			break;
		case 1:
			this.StateMinor[ID.M.NumAmmoLight] = value;
			break;
		case 2:
			this.StateMinor[ID.M.NumAmmoHeavy] = value;
			break;
		case 3:
			this.StateMinor[ID.M.NumAirLight] = value;
			break;
		case 4:
			this.StateMinor[ID.M.NumAirHeavy] = value;
			break;
		case 5:
			this.setStateFlagI(ID.F.UseMelee, value);
			break;
		case 6:
			this.setStateFlagI(ID.F.UseAmmoLight, value);
			break;
		case 7:
			this.setStateFlagI(ID.F.UseAmmoHeavy, value);
			break;
		case 8:
			this.setStateFlagI(ID.F.UseAirLight, value);
			break;
		case 9:
			this.setStateFlagI(ID.F.UseAirHeavy, value);
			break;
		case 10:
			this.setStateFlagI(ID.F.IsMarried, value);
			break;
		case 11:
			this.StateMinor[ID.M.FollowMin] = value;
			break;
		case 12:
			this.StateMinor[ID.M.FollowMax] = value;
			break;
		case 13:
			this.StateMinor[ID.M.FleeHP] = value;
			break;
		case 14:
			this.setStateFlagI(ID.F.PassiveAI, value);
			break;
		case 15:
			this.setStateFlagI(ID.F.UseRingEffect, value);
			break;
		case 16:
			this.setStateFlagI(ID.F.OnSightChase, value);
			break;
		case 17:
			this.setStateFlagI(ID.F.PVPFirst, value);
			break;
		case 18:
			this.setStateFlagI(ID.F.AntiAir, value);
			break;
		case 19:
			this.setStateFlagI(ID.F.AntiSS, value);
			break;
		case 20:
			this.setStateFlagI(ID.F.TimeKeeper, value);
			break;
		case 21:
			this.StateMinor[ID.M.Morale] = value;
			break;
		case 22:
			this.StateMinor[ID.M.InvSize] = value;
			break;
		case 23:
			this.setStateFlagI(ID.F.PickItem, value);
			break;
		case 24:
			this.StateMinor[ID.M.WpStay] = value;
			break;
		case 25:
			this.StateMinor[ID.M.Kills] = value;
			break;
		case 26:
			this.StateMinor[ID.M.NumGrudge] = value;
			break;
		case 27:
			this.itemHandler.setInventoryPage(value);
			break;
		}
		
	}
	
	//固定swing max tick為6, 無視藥水效果
  	@Override
    protected void updateArmSwingProgress()
  	{
        int swingMaxTick = 6;
        if (this.isSwingInProgress)
        {
            ++this.swingProgressInt;
            
            if (this.swingProgressInt >= swingMaxTick)
            {
                this.swingProgressInt = 0;
                this.isSwingInProgress = false;
            }
        }
        else
        {
            this.swingProgressInt = 0;
        }

        this.swingProgress = (float)this.swingProgressInt / (float)swingMaxTick;
    }
  	
	@Override
	public int getTextureID()
	{
		return this.getShipClass();
	}
	
	//for model display
	@Override
	public int getRidingState()
	{
		return 0;
	}
	
	@Override
	public void setRidingState(int state) {}
  	
	
}
