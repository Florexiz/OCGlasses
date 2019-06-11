package com.bymarcin.openglasses.item;

import java.util.HashSet;
import java.util.List;
import java.util.UUID;

import baubles.api.BaubleType;
import baubles.api.IBauble;
import com.bymarcin.openglasses.item.upgrades.*;
import com.bymarcin.openglasses.manual.IItemWithDocumentation;
import com.bymarcin.openglasses.network.NetworkRegistry;
import com.bymarcin.openglasses.network.packet.GlassesStackNBT;
import com.bymarcin.openglasses.surface.OCClientSurface;
import com.bymarcin.openglasses.surface.OCServerSurface;
import net.minecraft.client.Minecraft;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.ItemArmor;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.NonNullList;
import net.minecraftforge.energy.EnergyStorage;
import net.minecraftforge.fml.common.Optional;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import com.bymarcin.openglasses.OpenGlasses;

import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ICapabilityProvider;
import net.minecraftforge.energy.CapabilityEnergy;
import net.minecraftforge.energy.IEnergyStorage;
import net.minecraft.util.EnumFacing;

import net.minecraft.world.World;
import org.lwjgl.input.Keyboard;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

@Optional.Interface(iface="baubles.api.IBauble",modid="baubles")
public class OpenGlassesItem extends ItemArmor implements IItemWithDocumentation, IBauble {
	public static ItemStack DEFAULT_STACK;

	public static HashSet<UpgradeItem> upgrades = new HashSet<>();

	static {
		upgrades.add(new UpgradeBatteryTier1());
		upgrades.add(new UpgradeBatteryTier2());
		upgrades.add(new UpgradeBatteryTier3());
		upgrades.add(new UpgradeDatabase1());
		upgrades.add(new UpgradeDatabase2());
		upgrades.add(new UpgradeDatabase3());
		upgrades.add(new UpgradeDaylightDetector());
		upgrades.add(new UpgradeGeolyzer());
		upgrades.add(new UpgradeMotionSensor());
		upgrades.add(new UpgradeNightvision());
		upgrades.add(new UpgradeTank());
		upgrades.add(new UpgradeNavigation());
	}

	public OpenGlassesItem() {
		super(ArmorMaterial.IRON, 0, EntityEquipmentSlot.HEAD);
		setMaxDamage(0);
		setMaxStackSize(1);
		setHasSubtypes(true);
		setCreativeTab(OpenGlasses.creativeTab);
		setUnlocalizedName("openglasses");
		setRegistryName("openglasses");
	}

	public static void initGlassesStack(ItemStack glassesStack){
		glassesStack.setTagCompound(new NBTTagCompound());

		NBTTagCompound glassesTag = glassesStack.getTagCompound();
		glassesTag.setInteger("widgetLimit", 9); //default to max 9 Widgets
		glassesTag.setInteger("upkeepCost", 1);  //default to upkeep cost of 1FE / tick
		glassesTag.setInteger("radarRange", 0);
		glassesTag.setInteger("Energy", 0);
		glassesTag.setInteger("EnergyCapacity", 50000); //set the default EnergyBuffer to 50k FE
	}

	@Override
	@SideOnly(Side.CLIENT)
	public void getSubItems(CreativeTabs tab, NonNullList<ItemStack> subItems) {
		if(!this.isInCreativeTab(tab)) return;

		//configure creative glasses
		ItemStack creativeGlasses = DEFAULT_STACK.copy();
		NBTTagCompound creativeTag = creativeGlasses.getTagCompound();
		creativeTag.setInteger("Energy", 5000000);
		creativeTag.setInteger("EnergyCapacity", 5000000);
		creativeTag.setInteger("widgetLimit", 255);
		creativeTag.setInteger("radarRange", 128); //set the maximum radar range to 128

		for(UpgradeItem upgrade : upgrades)
			creativeGlasses = upgrade.install(creativeGlasses);

		creativeTag.setInteger("upkeepCost", 0);

		subItems.add(DEFAULT_STACK);
		subItems.add(creativeGlasses);
	}

	@Override
	public net.minecraftforge.common.capabilities.ICapabilityProvider initCapabilities(ItemStack stack, NBTTagCompound nbt){
		if(stack.getTagCompound() == null)
			stack.setTagCompound(new NBTTagCompound());

		if(nbt != null) {
			NBTTagCompound stackNBT = stack.getTagCompound();
			stackNBT.merge(nbt);
		}

		return new EnergyCapabilityProvider(stack);
	}

	@Override
	public String getArmorTexture(ItemStack stack, Entity entity, EntityEquipmentSlot slot, String type) {
		return OpenGlasses.MODID + ":textures/models/glasses.png";
	}

	@Override
	@SideOnly(Side.CLIENT)
	public void addInformation(ItemStack stack, @Nullable World worldIn, List<String> tooltip, ITooltipFlag flagIn) {
		super.addInformation(stack, worldIn, tooltip, flagIn);

		NBTTagCompound tag = stack.getTagCompound();

		/*if(tag.hasUniqueId("host")){
			UUID host = tag.getUniqueId("host");

			//if(!FMLCommonHandler.instance().getMinecraftServerInstance().getWorld(0).getGameRules().getBoolean("reducedDebugInfo"))
			//	tooltip.add("linked to: X: " + location.pos.getX() + ", Y: " + location.pos.getY() + ", Z: " + location.pos.getZ() + " (DIM: " + location.dimID +")");
			tooltip.add("host: " + host.toString());
			tooltip.add("user: " + tag.getString("user"));
		}
		else
			tooltip.add("use at glassesterminal to link glasses");*/

		tooltip.add("linked to "+getHostsFromNBT(stack).size()+" hosts");

		if(Keyboard.isKeyDown(Keyboard.KEY_LSHIFT)) {
			for (UpgradeItem upgrade : upgrades)
				tooltip.addAll(upgrade.getTooltip(stack));
		}
		else tooltip.add("hold [§fLSHIFT§7] to show upgrades");

		int widgetCount = OCClientSurface.instances.getWidgetCount(null, null);
		tooltip.add("§3using " + widgetCount + "/" + tag.getInteger("widgetLimit") + " widgets§7");

		int energyUsage = tag.getInteger("upkeepCost");

		IEnergyStorage storage = stack.getCapability(CapabilityEnergy.ENERGY, null);
		tooltip.add(String.format("§a%s/%s FE§7", storage.getEnergyStored(), storage.getMaxEnergyStored()));
		tooltip.add("§2usage " + energyUsage + " FE/tick§7");
	}

	public static void upgradeUpkeepCost(ItemStack stack){
		int upkeepCost = 1;
		for(UpgradeItem upgrade : upgrades)
			upkeepCost+=upgrade.getEnergyUsageCurrent(stack);

		stack.getTagCompound().setInteger("upkeepCost", upkeepCost);
	}

	public String getDocumentationName(ItemStack stack){
		return "Glasses";
	}

	public static void link(ItemStack glassesStack, UUID hostUUID, EntityPlayer player) {
		if(player.world.isRemote)
		    return;

	    NBTTagCompound newTag = new NBTTagCompound();

		newTag.setUniqueId("host", hostUUID);
		newTag.setUniqueId("userUUID", player.getGameProfile().getId());
		newTag.setString("user", player.getGameProfile().getName());

		HashSet<NBTTagCompound> hosts = getHostsFromNBT(glassesStack);
		hosts.add(newTag);
		writeHostsToNBT(hosts, glassesStack);

		syncStackNBT(glassesStack, (EntityPlayerMP) player);
		OCServerSurface.instance().subscribePlayer((EntityPlayerMP) player, hostUUID);
	}

	public static void unlink(UUID uuid, ItemStack glassesStack, EntityPlayer player) {
		if(player.world.isRemote)
			return;

		HashSet<NBTTagCompound> hosts = getHostsFromNBT(glassesStack);
		hosts.remove(getHostFromNBT(uuid, glassesStack));
		writeHostsToNBT(hosts, glassesStack);

		syncStackNBT(glassesStack, (EntityPlayerMP) player);
		OCServerSurface.instance().unsubscribePlayer(player.getUniqueID());
	}

	public static NBTTagCompound getHostFromNBT(UUID hostUUID, ItemStack glassesStack){
		if(glassesStack.isEmpty())
			return null;

		HashSet<NBTTagCompound> hosts = getHostsFromNBT(glassesStack);
		for(NBTTagCompound hostNBT : hosts)
			if(hostNBT.getUniqueId("host").equals(hostUUID))
				return hostNBT;

		return null;
	}

	private static void writeHostsToNBT(HashSet<NBTTagCompound> hosts, ItemStack glassesStack){
		int i=0;
		NBTTagCompound hostsNBT = new NBTTagCompound();
		for(NBTTagCompound tag : hosts)
			hostsNBT.setTag("host"+i++, tag);
		glassesStack.getTagCompound().setTag("hosts", hostsNBT);
	}

	public static void writeHostToNBT(ItemStack glassesStack, NBTTagCompound hostNBT){
		HashSet<NBTTagCompound> hosts = getHostsFromNBT(glassesStack);
		for(NBTTagCompound host : hosts)
			if(host.getUniqueId("host").equals(hostNBT.getUniqueId("host"))) {
				hosts.remove(host);
				break;
			}

		hosts.add(hostNBT);
		writeHostsToNBT(hosts, glassesStack);
	}

	public static HashSet<NBTTagCompound> getHostsFromNBT(ItemStack glassesStack){
		HashSet<NBTTagCompound> hosts = new HashSet<>();

		NBTTagCompound tag = glassesStack.getTagCompound();
		if(tag.hasKey("hosts")) {
			NBTTagCompound nbt = tag.getCompoundTag("hosts");
			for (int i = 0; nbt.hasKey("host" + i); i++)
				hosts.add(nbt.getCompoundTag("host" + i));
		}
		return hosts;
	}

	public static void setConfigFlag(String flagName, boolean enabled, ItemStack glassesStack, EntityPlayerMP player){
		if(player.world.isRemote)
			return;

		// config flags default to true if the tag doesnt exist, so tags are only set to DISABLE features

		if(enabled)
    		glassesStack.getTagCompound().removeTag(flagName);
    	else
			glassesStack.getTagCompound().setBoolean(flagName, true);

		syncStackNBT(glassesStack, player);
	}

	public static void syncStackNBT(ItemStack glassesStack, EntityPlayerMP player){
		NetworkRegistry.packetHandler.sendTo(new GlassesStackNBT(glassesStack), player);
	}

	// Forge Energy
	@Override
	public void onUpdate(ItemStack glassesStack, World world, Entity entity, int slot, boolean isCurrentItem) {
		if(world.isRemote) return;
		if (!(entity instanceof EntityPlayer)) return;

		ItemStack glasses = OpenGlasses.getGlassesStack((EntityPlayer) entity);
		if(glasses.isEmpty()) return;

		if(glasses.equals(glassesStack))
			consumeEnergy(glassesStack);
	}

	@Override
	public boolean showDurabilityBar(ItemStack glassesStack){
		IEnergyStorage storage = getEnergyStorage(glassesStack);
		return storage.getEnergyStored() < storage.getMaxEnergyStored();
	}

	@Override
	public double getDurabilityForDisplay(ItemStack glassesStack){
		IEnergyStorage storage = getEnergyStorage(glassesStack);
		return 1 - (((double) 1 / storage.getMaxEnergyStored()) * storage.getEnergyStored());
	}

	private static void consumeEnergy(ItemStack glassesStack){
		getEnergyStorage(glassesStack).extractEnergy(glassesStack.getTagCompound().getInteger("upkeepCost"), false);
	}

	public static double getEnergyStored(ItemStack glassesStack){
		if(!glassesStack.hasCapability(CapabilityEnergy.ENERGY, null)) return 0;
		return getEnergyStorage(glassesStack).getEnergyStored();
	}

	private static IEnergyStorage getEnergyStorage(ItemStack glassesStack){
		return glassesStack.getCapability(CapabilityEnergy.ENERGY, null);
	}

	private static class EnergyCapabilityProvider implements ICapabilityProvider{
		final EnergyStorage storage;

		EnergyCapabilityProvider(final ItemStack stack){
			this.storage = new EnergyStorage(0, 1000, 1000){
				@Override
				public int getEnergyStored(){
					return stack.getTagCompound().getInteger("Energy");
				}

				@Override
				public int getMaxEnergyStored(){
					return stack.getTagCompound().getInteger("EnergyCapacity");
				}

				void setEnergyStored(int energy){
					stack.getTagCompound().setInteger("Energy", energy);
				}

				@Override
				public int receiveEnergy(int receive, boolean simulate){
					int energy = this.getEnergyStored();

					int energyReceived = Math.min(this.getMaxEnergyStored()-energy, Math.min(this.maxReceive, receive));

					if(!simulate) this.setEnergyStored(energy+energyReceived);

					return energyReceived;
				}

				@Override
				public int extractEnergy(int extract, boolean simulate){
					if(!this.canExtract()) return 0;

					int energy = this.getEnergyStored();

					int energyExtracted = Math.min(energy, Math.min(this.maxExtract, extract));
					if(!simulate) this.setEnergyStored(energy-energyExtracted);

					return energyExtracted;
				}
			};
		}

		@Override
		public boolean hasCapability(@Nonnull Capability<?> capability, EnumFacing facing){
			return this.getCapability(capability, facing) != null;
		}

		@SuppressWarnings("unchecked")
		@Override
		public <T> T getCapability(@Nonnull Capability<T> capability, EnumFacing facing){
			if(capability == CapabilityEnergy.ENERGY){
				return (T) this.storage;
			}
			return null;
		}
	}

	/* Baubles integration */
	@Override
	@Optional.Method(modid="baubles")
	public BaubleType getBaubleType(ItemStack itemstack) {
		return BaubleType.HEAD;
	}

	@Override
	@Optional.Method(modid="baubles")
	public boolean canEquip(ItemStack itemstack, EntityLivingBase player) {
		return true;
	}

	@Override
	@Optional.Method(modid="baubles")
	public boolean canUnequip(ItemStack itemstack, EntityLivingBase player) {
		return true;
	}

	@Override
	@Optional.Method(modid="baubles")
	public boolean willAutoSync(ItemStack itemstack, EntityLivingBase player) { return true; }

	@Override
	@SideOnly(Side.SERVER)
	@Optional.Method(modid="baubles")
	public void onWornTick(ItemStack itemstack, EntityLivingBase player){ consumeEnergy(itemstack); }

}