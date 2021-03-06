package com.lulan.shincolle.item;

import java.util.HashMap;
import java.util.List;

import com.lulan.shincolle.capability.CapaTeitoku;
import com.lulan.shincolle.entity.BasicEntityShip;
import com.lulan.shincolle.entity.BasicEntityShipHostile;
import com.lulan.shincolle.entity.BasicEntitySummon;
import com.lulan.shincolle.network.C2SGUIPackets;
import com.lulan.shincolle.network.C2SInputPackets;
import com.lulan.shincolle.proxy.CommonProxy;
import com.lulan.shincolle.proxy.ServerProxy;
import com.lulan.shincolle.tileentity.ITileWaypoint;
import com.lulan.shincolle.tileentity.TileEntityCrane;
import com.lulan.shincolle.utility.EntityHelper;
import com.lulan.shincolle.utility.LogHelper;

import net.minecraft.client.resources.I18n;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ActionResult;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.registry.GameRegistry;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/** target selector for OP only
 * 
 *  left click: add/remove attackable target
 *  right click: show target list
 */
public class TargetWrench extends BasicItem
{
	
	private static final String NAME = "TargetWrench";
	private BlockPos[] tilePoint;
	private int pointID;
	
	
	public TargetWrench()
	{
		super();
		this.setUnlocalizedName(NAME);
		this.setRegistryName(NAME);
		this.setMaxStackSize(1);
		this.setFull3D();
        
        GameRegistry.register(this);
		
		this.tilePoint = new BlockPos[] {BlockPos.ORIGIN, BlockPos.ORIGIN};
		this.pointID = 0;
	}
	
	//item glow effect
	@Override
	@SideOnly(Side.CLIENT)
    public boolean hasEffect(ItemStack item)
	{
        return true;
    }
	
	/** left click: add / remove attackable target
	 * 
	 *  excluding BasicEntityShip and BasicEntityShipHostile
	 *  
	 *  process:
	 *  1. get mouseover entity (client)
	 *  2. send player eid and entity to server (c 2 s)
	 *  3. check player is OP (server)
	 *  4. add/remove entity to list (server)
	 */
	@Override
	public boolean onEntitySwing(EntityLivingBase entity, ItemStack item)
	{
		int meta = item.getItemDamage();
		
		if (entity instanceof EntityPlayer)
		{
			EntityPlayer player = (EntityPlayer) entity;
			
			//玩家左鍵使用此武器時 (client side only)
			if (player.world.isRemote)
			{
				RayTraceResult hitObj = EntityHelper.getPlayerMouseOverEntity(64D, 1F);
				
				//hit entity
				if (hitObj != null && hitObj.entityHit != null)
				{
					//target != ship
					if (!(hitObj.entityHit instanceof BasicEntityShip ||
						  hitObj.entityHit instanceof BasicEntityShipHostile ||
						  hitObj.entityHit instanceof BasicEntitySummon))
					{
						String tarName = hitObj.entityHit.getClass().getSimpleName();
						LogHelper.debug("DEBUG: target wrench get class: "+tarName);
						
						//send packet to server
						CommonProxy.channelG.sendToServer(new C2SGUIPackets(player, C2SGUIPackets.PID.SetUnatkClass, tarName));
						return false;
					}//end not ship
				}//end hit != null
			}//end client side
			else
			{
				if (player.isSneaking())
				{
					HashMap<Integer, String> tarlist = ServerProxy.getUnattackableTargetClass();
					
					TextComponentTranslation text = new TextComponentTranslation("chat.shincolle:wrench.unatkshow");
					text.getStyle().setColor(TextFormatting.GOLD);
					player.sendMessage(text);
					
					tarlist.forEach((k, v) ->
					{
						player.sendMessage(new TextComponentString(TextFormatting.AQUA+v));
					});
					
					return true;
				}
			}
		}//end player not null
		
        return false;	//both side
    }
	
	/**
	 * right click on block
	 * sneaking: pair Chest, Crane and Waypoint
	 */
	@Override
	public EnumActionResult onItemUse(ItemStack stack, EntityPlayer player, World world, BlockPos pos, EnumHand hand, EnumFacing facing, float hitX, float hitY, float hitZ)
	{
		if (player != null)
		{
			//client side
			if (world.isRemote)
			{
				//sneaking
				if (player.isSneaking())
				{
					TileEntity tile = world.getTileEntity(pos);
					
					if (tile instanceof TileEntityCrane ||
						tile instanceof IInventory ||
						tile instanceof ITileWaypoint)
					{
						this.tilePoint[this.pointID] = pos;
						this.switchPoint();
						this.setPair(player);
						
						return EnumActionResult.FAIL;	//return fail to prevent item swing
					}
					else
					{
						//fail msg
						player.sendMessage(new TextComponentTranslation("chat.shincolle:wrench.wrongtile"));
						
						//clear data
						resetPos();
					}
				}
			}//end client side
		}//end get player
		
		return EnumActionResult.PASS;
    }
	
	@Override
    public ActionResult<ItemStack> onItemRightClick(ItemStack itemStackIn, World worldIn, EntityPlayer playerIn, EnumHand hand)
    {
        return new ActionResult(EnumActionResult.PASS, itemStackIn);
    }
	
	@Override
    public void addInformation(ItemStack itemstack, EntityPlayer player, List list, boolean par4)
	{
    	list.add(TextFormatting.RED + I18n.format("gui.shincolle:wrench1"));
    	list.add(TextFormatting.AQUA + I18n.format("gui.shincolle:wrench2"));
    	list.add(TextFormatting.YELLOW + I18n.format("gui.shincolle:wrench3"));
	}
	
	// 0 <-> 1
	private void switchPoint()
	{
		this.pointID = this.pointID == 0 ? 1 : 0;
	}
	
	private void resetPos()
	{
		this.tilePoint = new BlockPos[] {BlockPos.ORIGIN, BlockPos.ORIGIN};
		this.pointID = 0;
	}

	//crane pairing, CLIENT SIDE ONLY
	private boolean setPair(EntityPlayer player)
	{
		//valid point position
		if (tilePoint[0].getY() <= 0 || tilePoint[1].getY() <= 0) return false;
		
		//get player UID
		CapaTeitoku capa = CapaTeitoku.getTeitokuCapability(player);
		int uid = 0;
		if (capa != null) uid = capa.getPlayerUID();
		if (uid <= 0) return false;
		
		//get tile
		TileEntity[] tiles = new TileEntity[2];
		tiles[0] = player.world.getTileEntity(tilePoint[0]);
		tiles[1] = player.world.getTileEntity(tilePoint[1]);
		
		//calc distance
		int dx = this.tilePoint[0].getX() - this.tilePoint[1].getX();
		int dy = this.tilePoint[0].getY() - this.tilePoint[1].getY();
		int dz = this.tilePoint[0].getZ() - this.tilePoint[1].getZ();
		int dist = dx * dx + dy * dy + dz * dz;
		
		//is same point
		if (dx == 0 && dy == 0 && dz == 0)
		{
			player.sendMessage(new TextComponentTranslation("chat.shincolle:wrench.samepoint"));
			
			//clear data
			resetPos();
			
			return false;
		}

		//chest and crane pairing
		if (tiles[0] instanceof IInventory && !(tiles[0] instanceof TileEntityCrane) && tiles[1] instanceof TileEntityCrane ||
			tiles[1] instanceof IInventory && !(tiles[1] instanceof TileEntityCrane) && tiles[0] instanceof TileEntityCrane)
		{
			//check dist < ~6 blocks
			if (dist < 40)
			{
				BlockPos cranePos = null;
				BlockPos chestPos = null;
				
				//set chest pair
				if (tiles[0] instanceof TileEntityCrane)
				{
					cranePos = tilePoint[0];
					chestPos = tilePoint[1];
				}
				else
				{
					cranePos = tilePoint[1];
					chestPos = tilePoint[0];
				}
				
				//send pairing request packet
				CommonProxy.channelI.sendToServer(new C2SInputPackets(C2SInputPackets.PID.Request_ChestSet,
					uid, cranePos.getX(), cranePos.getY(), cranePos.getZ(),
					chestPos.getX(), chestPos.getY(), chestPos.getZ()));
				
				//clear data
				resetPos();
				
				return true;
			}
			//send too far away msg
			else
			{
				TextComponentTranslation str = new TextComponentTranslation("chat.shincolle:wrench.toofar");
				str.getStyle().setColor(TextFormatting.YELLOW);
				player.sendMessage(str);
				
				//clear data
				resetPos();
				
				return false;
			}
		}
		//waypoint pairing
		else if (tiles[0] instanceof ITileWaypoint && tiles[1] instanceof ITileWaypoint)
		{
			//dist < 48 blocks
			if (dist < 2304)
			{
				//get waypoint order
				ITileWaypoint wpFrom = (ITileWaypoint) tiles[this.pointID];
				BlockPos posF = tilePoint[this.pointID];
				this.switchPoint();
				ITileWaypoint wpTo = (ITileWaypoint) tiles[this.pointID];
				BlockPos posT = tilePoint[this.pointID];
				
				//send pairing request packet
				CommonProxy.channelI.sendToServer(new C2SInputPackets(C2SInputPackets.PID.Request_WpSet,
					uid, posF.getX(), posF.getY(), posF.getZ(), posT.getX(), posT.getY(), posT.getZ()));
				
				//clear data
				resetPos();
				
				return true;
			}
			//send too far away msg
			else
			{
				TextComponentTranslation str = new TextComponentTranslation("chat.shincolle:wrench.wptoofar");
				str.getStyle().setColor(TextFormatting.YELLOW);
				player.sendMessage(str);
				
				//clear data
				resetPos();
				
				return false;
			}
		}
		else
		{
			TextComponentTranslation str = new TextComponentTranslation("chat.shincolle:wrench.wrongtile");
			str.getStyle().setColor(TextFormatting.YELLOW);
			player.sendMessage(str);
			
			//clear data
			resetPos();
			
			return false;
		}
	}
	

}