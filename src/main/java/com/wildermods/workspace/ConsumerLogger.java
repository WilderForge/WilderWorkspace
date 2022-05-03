package com.wildermods.workspace;

import java.io.IOException;

import net.fabricmc.loom.util.IOStringConsumer;

public class ConsumerLogger implements IOStringConsumer {

	@Override
	public void accept(String data) throws IOException {
		System.out.println(data);
	}

}
