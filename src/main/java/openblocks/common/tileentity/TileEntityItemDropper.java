package openblocks.common.tileentity;

import javax.vecmath.Matrix3f;
import javax.vecmath.Matrix4f;
import javax.vecmath.Vector3f;
import javax.vecmath.Vector4f;
import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.items.CapabilityItemHandler;
import openblocks.client.gui.GuiItemDropper;
import openblocks.common.container.ContainerItemDropper;
import openblocks.rpc.IItemDropper;
import openmods.api.IHasGui;
import openmods.api.INeighbourAwareTile;
import openmods.fakeplayer.DropItemAction;
import openmods.fakeplayer.FakePlayerPool;
import openmods.geometry.Orientation;
import openmods.include.IncludeInterface;
import openmods.inventory.GenericInventory;
import openmods.inventory.IInventoryProvider;
import openmods.inventory.TileEntityInventory;
import openmods.sync.SyncableBoolean;
import openmods.sync.SyncableDouble;
import openmods.tileentity.SyncedTileEntity;

public class TileEntityItemDropper extends SyncedTileEntity implements INeighbourAwareTile, IInventoryProvider, IHasGui, IItemDropper {
	static final int BUFFER_SIZE = 9;

	private boolean redstoneState;

	private GenericInventory inventory = registerInventoryCallback(new TileEntityInventory(this, "itemDropper", false, 9));

	private SyncableDouble itemSpeedBase;

	private SyncableBoolean useRedstoneStrength;

	public TileEntityItemDropper() {}

	@Override
	protected void createSyncedFields() {
		this.itemSpeedBase = new SyncableDouble(0);
		this.useRedstoneStrength = new SyncableBoolean(false);
	}

	private void setRedstoneSignal(int redstoneSignal) {
		boolean newRedstoneState = redstoneSignal > 0;
		if (newRedstoneState != redstoneState) {
			redstoneState = newRedstoneState;
			if (redstoneState) {
				final float speedMultiplier = (float)(itemSpeedBase.get() * (useRedstoneStrength.get()? redstoneSignal / 15.0f : 1.0f));
				dropItem(speedMultiplier);
			}
		}
	}

	private static final Vector4f DROP_POS = new Vector4f(0.5f, -0.5f, 0.5f, 1.0f);
	private static final Vector3f DROP_V = new Vector3f(0.0f, -1.0f, 0.0f);

	private void dropItem(float speedMultiplier) {
		if (!(world instanceof WorldServer)) return;

		for (int i = 0; i < inventory.getSizeInventory(); i++) {
			ItemStack stack = inventory.getStackInSlot(i);
			if (stack.isEmpty()) continue;

			final ItemStack dropped = stack.splitStack(1);
			inventory.setInventorySlotContents(i, dropped);

			final Orientation orientation = getOrientation();

			final Vector4f worldDrop = new Vector4f();
			{
				final Matrix4f blockLocalToWorld = orientation.getBlockLocalToWorldMatrix();
				blockLocalToWorld.transform(DROP_POS, worldDrop);
			}

			final Vector3f worldVel = new Vector3f();
			{
				final Matrix3f localToWorld = orientation.getLocalToWorldMatrix();
				worldVel.set(DROP_V);
				worldVel.scale(speedMultiplier);
				localToWorld.transform(worldVel);
			}

			final double worldDropX = pos.getX() + worldDrop.x;
			final double worldDropY = pos.getY() + worldDrop.y;
			final double worldDropZ = pos.getZ() + worldDrop.z;

			final DropItemAction action = new DropItemAction(dropped, worldDropX, worldDropY, worldDropZ, worldVel.x, worldVel.y, worldVel.z);
			FakePlayerPool.instance.executeOnPlayer((WorldServer)world, action);

			break;
		}
	}

	@Override
	public void onNeighbourChanged(BlockPos pos, Block block) {
		if (!world.isRemote) {
			setRedstoneSignal(world.isBlockIndirectlyGettingPowered(pos));
		}
	}

	@Override
	public Object getServerGui(EntityPlayer player) {
		return new ContainerItemDropper(player.inventory, this);
	}

	@Override
	public Object getClientGui(EntityPlayer player) {
		return new GuiItemDropper(new ContainerItemDropper(player.inventory, this));
	}

	@Override
	public boolean canOpenGui(EntityPlayer player) {
		return true;
	}

	@Override
	@IncludeInterface
	public IInventory getInventory() {
		return inventory;
	}

	@Override
	public NBTTagCompound writeToNBT(NBTTagCompound tag) {
		tag = super.writeToNBT(tag);
		inventory.writeToNBT(tag);
		return tag;
	}

	@Override
	public void readFromNBT(NBTTagCompound tag) {
		super.readFromNBT(tag);
		inventory.readFromNBT(tag);
	}

	@Override
	public void setItemSpeed(double speed) {
		this.itemSpeedBase.set(speed);
		sync();
	}

	public double getItemSpeed() {
		return this.itemSpeedBase.get();
	}

	@Override
	public void setUseRedstoneStrength(boolean useRedstone) {
		this.useRedstoneStrength.set(useRedstone);
		sync();
	}

	public boolean getUseRedstoneStrength() {
		return this.useRedstoneStrength.get();
	}

	public IItemDropper createRpcProxy() {
		return createClientRpcProxy(IItemDropper.class);
	}

	@Override
	public boolean hasCapability(Capability<?> capability, EnumFacing facing) {
		return capability == CapabilityItemHandler.ITEM_HANDLER_CAPABILITY ||
				super.hasCapability(capability, facing);
	}

	@Override
	@SuppressWarnings("unchecked")
	public <T> T getCapability(Capability<T> capability, EnumFacing facing) {
		if (capability == CapabilityItemHandler.ITEM_HANDLER_CAPABILITY)
			return (T)inventory.getHandler();

		return super.getCapability(capability, facing);
	}
}
