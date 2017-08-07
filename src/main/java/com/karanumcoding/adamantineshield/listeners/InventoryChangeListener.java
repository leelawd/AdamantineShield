package com.karanumcoding.adamantineshield.listeners;

import java.util.Date;

import org.spongepowered.api.block.tileentity.carrier.Chest;
import org.spongepowered.api.block.tileentity.carrier.TileEntityCarrier;
import org.spongepowered.api.entity.Entity;
import org.spongepowered.api.entity.living.player.Player;
import org.spongepowered.api.event.Listener;
import org.spongepowered.api.event.cause.entity.spawn.EntitySpawnCause;
import org.spongepowered.api.event.filter.cause.First;
import org.spongepowered.api.event.item.inventory.AffectSlotEvent;
import org.spongepowered.api.item.ItemTypes;
import org.spongepowered.api.item.inventory.ItemStack;
import org.spongepowered.api.item.inventory.ItemStackSnapshot;
import org.spongepowered.api.item.inventory.property.SlotIndex;
import org.spongepowered.api.item.inventory.transaction.SlotTransaction;
import org.spongepowered.api.item.inventory.type.CarriedInventory;

import com.karanumcoding.adamantineshield.db.Database;
import com.karanumcoding.adamantineshield.db.queue.InventoryQueueEntry;
import com.karanumcoding.adamantineshield.enums.ActionType;

public class InventoryChangeListener {

	private Database db;
	private boolean logContainers;
	
	public InventoryChangeListener(Database db, boolean logContainers) {
		this.db = db;
		this.logContainers = logContainers;
	}
	
	@Listener
	public void onInventoryTransfer(AffectSlotEvent e, @First EntitySpawnCause c) {
		Entity entity = c.getEntity();
		if (!(entity instanceof Player)) return;
		onInventoryTransfer(e, (Player) entity);
	}
	
	@Listener
	public void onInventoryTransfer(AffectSlotEvent e, @First Player p) {
		if (e.getTransactions().isEmpty()) return;
		if (!(e.getTransactions().get(0).getSlot().parent() instanceof CarriedInventory))
			return;
		
		CarriedInventory<?> c = (CarriedInventory<?>) e.getTransactions().get(0).getSlot().parent();
		if (!c.getCarrier().isPresent() || !(c.getCarrier().get() instanceof TileEntityCarrier))
			return;
		TileEntityCarrier carrier = (TileEntityCarrier) c.getCarrier().get();
		
		if (!logContainers && !(carrier instanceof Chest))
			return;
		
		long timestamp = new Date().getTime();
		int containerSize = c.iterator().next().capacity();
		for (SlotTransaction transaction : e.getTransactions()) {
			int slotId = transaction.getSlot().getProperty(SlotIndex.class, "slotindex").map(SlotIndex::getValue).orElse(-1);
			if (slotId >= containerSize)
				continue;
			
			ItemStackSnapshot origItem = transaction.getOriginal();
			ItemStackSnapshot finalItem = transaction.getFinal();
			if (origItem == finalItem)
				continue;
			
			if (origItem.getType() == finalItem.getType()) {
				if (origItem.getCount() > finalItem.getCount()) {
					ItemStackSnapshot stack = ItemStack.builder().itemType(origItem.getType())
							.quantity(origItem.getCount() - finalItem.getCount())
							.build().createSnapshot();
					db.addToQueue(new InventoryQueueEntry(carrier, slotId, stack, ActionType.CONTAINER_REMOVE, p, timestamp));
				} else {
					ItemStackSnapshot stack = ItemStack.builder().itemType(origItem.getType())
							.quantity(finalItem.getCount() - origItem.getCount())
							.build().createSnapshot();
					db.addToQueue(new InventoryQueueEntry(carrier, slotId, stack, ActionType.CONTAINER_ADD, p, timestamp));
				}
			} else {
				if (origItem.getType() != ItemTypes.NONE) {
					db.addToQueue(new InventoryQueueEntry(carrier, slotId, origItem, ActionType.CONTAINER_REMOVE, p, timestamp));
				}
				
				if (finalItem.getType() != ItemTypes.NONE) {
					db.addToQueue(new InventoryQueueEntry(carrier, slotId, finalItem, ActionType.CONTAINER_ADD, p, timestamp));
				}
			}
		}
	}
	
}
