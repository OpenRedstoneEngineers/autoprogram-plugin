package org.openredstone.autoprogram;

import org.bukkit.Material;
import org.bukkit.block.BlockFace;

class BlockDef
{
	public String name;
	public int index;
	public Material material;
	public BlockFace face;

	public BlockDef()
	{
		this.name = "";
		this.index = 0;
	}
}
