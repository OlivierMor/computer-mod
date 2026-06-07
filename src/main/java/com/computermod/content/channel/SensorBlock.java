package com.computermod.content.channel;

import com.computermod.registry.ModBlockEntities;

import net.minecraft.world.level.block.entity.BlockEntityType;

/** Sensor block: a thin plate mounted against a block, reading whatever it is stuck to. */
public class SensorBlock extends WallChannelBlock<SensorBlockEntity> {

	public SensorBlock(Properties properties) {
		super(properties);
	}

	@Override
	public Class<SensorBlockEntity> getBlockEntityClass() {
		return SensorBlockEntity.class;
	}

	@Override
	public BlockEntityType<? extends SensorBlockEntity> getBlockEntityType() {
		return ModBlockEntities.SENSOR.get();
	}
}
