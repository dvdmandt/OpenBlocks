package openblocks.common.item;

import com.google.common.collect.MapMaker;
import java.util.Map;
import net.minecraft.block.properties.IProperty;
import net.minecraft.block.properties.PropertyEnum;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ActionResult;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumHand;
import net.minecraft.util.IStringSerializable;
import net.minecraft.world.World;
import openblocks.Config;
import openblocks.common.CraneRegistry;
import openblocks.common.entity.EntityMagnet;
import openmods.OpenMods;
import openmods.infobook.BookDocumentation;
import openmods.model.itemstate.IStateItem;
import openmods.state.State;
import openmods.state.StateContainer;

@BookDocumentation(customName = "crane_control", hasVideo = true)
public class ItemCraneControl extends Item implements IStateItem {

	public ItemCraneControl() {
		setMaxStackSize(1);
	}

	private static final Map<EntityLivingBase, Long> debouncerTime = new MapMaker().weakKeys().makeMap();

	private static boolean hasClicked(EntityLivingBase entity) {
		long currentTime = OpenMods.proxy.getTicks(entity.world);
		Long lastClick = debouncerTime.get(entity);
		if (lastClick == null || currentTime - lastClick > 5) {
			debouncerTime.put(entity, currentTime);
			return true;
		}

		return false;
	}

	@Override
	public boolean onEntitySwing(EntityLivingBase entityLiving, ItemStack stack) {
		if (entityLiving instanceof EntityPlayer && hasClicked(entityLiving)) {
			final EntityPlayer player = (EntityPlayer)entityLiving;
			final EntityMagnet magnet = CraneRegistry.instance.getMagnetForPlayer(player);
			if (magnet != null) magnet.toggleMagnet();

		}
		return true;
	}

	@Override
	public boolean onLeftClickEntity(ItemStack stack, EntityPlayer player, Entity entity) {
		return true;
	}

	@Override
	public ActionResult<ItemStack> onItemRightClick(World world, EntityPlayer player, EnumHand hand) {
		CraneRegistry.Data data = CraneRegistry.instance.getData(player, false);

		if (data != null) {
			data.isExtending = Config.craneShiftControl? player.isSneaking() : !data.isExtending;
		}

		player.setActiveHand(hand);
		return ActionResult.newResult(EnumActionResult.SUCCESS, player.getHeldItem(hand));
	}

	@Override
	public void onUsingTick(ItemStack stack, EntityLivingBase player, int count) {
		if (player instanceof EntityPlayerMP
				&& ItemCraneBackpack.isWearingCrane(player)) {
			CraneRegistry.Data data = CraneRegistry.instance.getData(player, true);
			data.updateLength();
		}
	}

	@Override
	public int getMaxItemUseDuration(ItemStack stack) {
		return 72000; // quite long time!
	}

	private enum LiftState implements IStringSerializable {
		IDLE, DOWN, UP;

		@Override
		public String getName() {
			return name();
		}
	}

	private enum MagnetState implements IStringSerializable {
		IDLE, DETECTING, LOCKED;

		@Override
		public String getName() {
			return name();
		}
	}

	private static final IProperty<LiftState> liftProperty = PropertyEnum.create("lift", LiftState.class);

	private static final IProperty<MagnetState> magnetProperty = PropertyEnum.create("magnet", MagnetState.class);

	private final StateContainer stateContainer = new StateContainer(liftProperty, magnetProperty);

	private final State defaultState = stateContainer.getBaseState().withProperty(liftProperty, LiftState.IDLE).withProperty(magnetProperty, MagnetState.IDLE);

	@Override
	public StateContainer getStateContainer() {
		return stateContainer;
	}

	@Override
	public State getState(ItemStack stack, World world, EntityLivingBase entity) {
		if (entity != null && ItemCraneBackpack.isWearingCrane(entity)) {
			CraneRegistry.Data data = CraneRegistry.instance.getData(entity, false);
			if (data != null) {
				State state = defaultState;
				if (entity.getActiveItemStack() == stack) {
					state = state.withProperty(liftProperty, data.isExtending? LiftState.DOWN : LiftState.UP);
				}

				EntityMagnet magnet = CraneRegistry.instance.getMagnetForPlayer(entity);

				if (magnet != null) {
					if (magnet.isLocked()) state = state.withProperty(magnetProperty, MagnetState.LOCKED);
					else if (magnet.isAboveTarget()) state = state.withProperty(magnetProperty, MagnetState.DETECTING);
				}
				return state;
			}
		}

		return defaultState;
	}

}
