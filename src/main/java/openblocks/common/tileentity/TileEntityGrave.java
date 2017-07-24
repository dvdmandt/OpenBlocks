package openblocks.common.tileentity;

import com.google.common.base.Predicate;
import com.google.common.base.Strings;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.monster.EntitySkeleton;
import net.minecraft.entity.monster.IMob;
import net.minecraft.entity.passive.EntityBat;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.ITickable;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.world.EnumDifficulty;
import net.minecraft.world.storage.WorldInfo;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import openblocks.Config;
import openblocks.OpenBlocks;
import openmods.api.IActivateAwareTile;
import openmods.api.IAddAwareTile;
import openmods.api.INeighbourAwareTile;
import openmods.api.IPlaceAwareTile;
import openmods.inventory.GenericInventory;
import openmods.inventory.IInventoryProvider;
import openmods.sync.SyncableBoolean;
import openmods.sync.SyncableString;
import openmods.tileentity.SyncedTileEntity;
import openmods.utils.BlockUtils;

public class TileEntityGrave extends SyncedTileEntity implements IPlaceAwareTile, IInventoryProvider, INeighbourAwareTile, IActivateAwareTile, IAddAwareTile, ITickable {

	private static final Predicate<EntityLiving> IS_MOB = new Predicate<EntityLiving>() {
		@Override
		public boolean apply(@Nullable EntityLiving input) {
			return input instanceof IMob;
		}
	};

	private static final String TAG_MESSAGE = "Message";
	private SyncableString perishedUsername;
	public SyncableBoolean onSoil;

	private ITextComponent deathMessage;

	private GenericInventory inventory = registerInventoryCallback(new GenericInventory("grave", false, 1));

	public TileEntityGrave() {}

	@Override
	protected void createSyncedFields() {
		perishedUsername = new SyncableString();
		onSoil = new SyncableBoolean(true);
	}

	@Override
	public void update() {
		if (!world.isRemote) {
			if (Config.spawnSkeletons && world.getDifficulty() != EnumDifficulty.PEACEFUL && world.rand.nextDouble() < Config.skeletonSpawnRate) {

				List<EntityLiving> mobs = world.getEntitiesWithinAABB(EntityLiving.class, getBB().expand(7, 7, 7), IS_MOB);

				if (mobs.size() < 5) {
					double chance = world.rand.nextDouble();
					EntityLiving living = chance < 0.5? new EntitySkeleton(world) : new EntityBat(world);
					living.setPositionAndRotation(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, world.rand.nextFloat() * 360, 0);
					if (living.getCanSpawnHere()) {
						world.spawnEntity(living);
					}
				}
			}
		}
	}

	public String getUsername() {
		return perishedUsername.getValue();
	}

	public void setDeathMessage(ITextComponent msg) {
		deathMessage = msg.createCopy();
	}

	public void setUsername(String username) {
		this.perishedUsername.setValue(username);
	}

	public void setLoot(IInventory invent) {
		inventory.clearAndSetSlotCount(invent.getSizeInventory());
		inventory.copyFrom(invent);
	}

	public boolean isOnSoil() {
		return onSoil.get();
	}

	@Override
	public void onBlockPlacedBy(IBlockState state, EntityLivingBase placer, ItemStack stack) {
		if (!world.isRemote) {
			if ((placer instanceof EntityPlayer) && !(placer instanceof FakePlayer)) {
				EntityPlayer player = (EntityPlayer)placer;

				if (stack.hasDisplayName()) setUsername(stack.getDisplayName());
				else setUsername(player.getGameProfile().getName());
				if (player.capabilities.isCreativeMode) setLoot(player.inventory);
				updateBlockBelow();
				sync();
			}
		}
	}

	@Override
	public NBTTagCompound writeToNBT(NBTTagCompound tag) {
		tag = super.writeToNBT(tag);
		inventory.writeToNBT(tag);

		if (deathMessage != null) {
			String serialized = ITextComponent.Serializer.componentToJson(deathMessage);
			tag.setString(TAG_MESSAGE, serialized);
		}

		return tag;
	}

	@Override
	public void readFromNBT(NBTTagCompound tag) {
		super.readFromNBT(tag);
		inventory.readFromNBT(tag);

		String serializedMsg = tag.getString(TAG_MESSAGE);

		if (!Strings.isNullOrEmpty(serializedMsg)) {
			deathMessage = ITextComponent.Serializer.jsonToComponent(serializedMsg);
		}
	}

	@Override
	public IInventory getInventory() {
		return inventory;
	}

	protected void updateBlockBelow() {
		IBlockState block = world.getBlockState(pos.down());
		final Material material = block.getMaterial();
		onSoil.set(material == Material.GRASS || material == Material.GROUND);
	}

	@Override
	public void onAdded() {
		updateBlockBelow();
	}

	@Override
	public void onNeighbourChanged(BlockPos pos, Block block) {
		updateBlockBelow();
		sync();
	}

	@Override
	@SideOnly(Side.CLIENT)
	public AxisAlignedBB getRenderBoundingBox() {
		return BlockUtils.singleBlock(pos);
	}

	@Override
	public boolean onBlockActivated(EntityPlayer player, EnumHand hand, EnumFacing side, float hitX, float hitY, float hitZ) {
		if (player.world.isRemote || hand != EnumHand.MAIN_HAND) return false;

		final ItemStack held = player.getHeldItemMainhand();
		if (!held.isEmpty() && held.getItem().getToolClasses(held).contains("shovel")) {
			robGrave(player, held);
		} else if (deathMessage != null) {
			player.sendMessage(deathMessage);
		}

		return true;
	}

	protected void robGrave(EntityPlayer player, ItemStack held) {
		boolean dropped = false;
		for (int i = 0; i < inventory.getSizeInventory(); i++) {
			final ItemStack stack = inventory.getStackInSlot(i);
			if (!stack.isEmpty()) {
				dropped = true;
				BlockUtils.dropItemStackInWorld(world, pos, stack);
			}
		}

		inventory.clearAndSetSlotCount(0);

		if (dropped) {
			world.playEvent(null, 2001, pos, Block.getIdFromBlock(Blocks.DIRT));
			if (world.rand.nextDouble() < Config.graveSpecialAction) ohNoes(player);
			held.damageItem(2, player);
		}
	}

	private void ohNoes(EntityPlayer player) {
		world.playSound(null, player.getPosition(), OpenBlocks.Sounds.BLOCK_GRAVE_ROB, SoundCategory.BLOCKS, 1, 1);

		final WorldInfo worldInfo = world.getWorldInfo();
		worldInfo.setThunderTime(35 * 20);
		worldInfo.setRainTime(35 * 20);
		worldInfo.setThundering(true);
		worldInfo.setRaining(true);
	}

}
