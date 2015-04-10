package org.openredstone.autoprogram;

import java.util.Hashtable;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.Material;
import org.bukkit.material.Torch;

class ProgParser
{
	private String defsPart;
	private String worldPart;
	private Player player;

	private World world;
	private Location loc;
	private BlockFace direction;

	private Hashtable<String, BlockDef> blockDefs;
	private int biggestIndex;

	public ProgParser(String prog, Player player) throws RuntimeException
	{
		String[] progParts = prog.split("\\|");
		if (progParts.length != 2)
			throw new RuntimeException("You need to supply both the defs and the world part.");

		this.defsPart = progParts[0];
		this.worldPart = progParts[1];

		this.player = player;

		this.loc = player.getLocation();
		this.world = loc.getWorld();

		//Calculate which direction the player is facing
		float yaw = ((loc.getYaw() % 360) + 360) % 360;
		if (yaw > 135 && yaw < 225)
			direction = BlockFace.NORTH;
		else if (yaw > 225 && yaw < 315)
			direction = BlockFace.EAST;
		else if (yaw > 315 || yaw < 45)
			direction = BlockFace.SOUTH;
		else
			direction = BlockFace.WEST;

		blockDefs = parseDefs(defsPart);
		this.biggestIndex = getBiggestIndex(blockDefs);
	}

	private boolean isNumeric(String str)
	{
		try
		{
			Integer.parseInt(str);
			return true;
		}
		catch (Exception e)
		{
			return false;
		}
	}

	private int getBiggestIndex(Hashtable<String, BlockDef> blocks)
	{
		int biggestIndex = 0;
		for (String key : blocks.keySet())
		{
			if (blocks.get(key).index > biggestIndex)
				biggestIndex = blocks.get(key).index;
		}
		return biggestIndex;
	}

	private BlockFace rotateFace(BlockFace face, int n) throws RuntimeException
	{
		for (int i=0; i<n; ++i)
		{
			switch (face)
			{
			case SOUTH:
				face = BlockFace.WEST;
				break;
			case WEST:
				face = BlockFace.NORTH;
				break;
			case NORTH:
				face = BlockFace.EAST;
				break;
			case EAST:
				face = BlockFace.SOUTH;
				break;
			default:
				throw new RuntimeException("That face isn't implemented.");
			}
		}

		return face;
	}

	private Hashtable<String, BlockDef> parseDefs(String defs) throws RuntimeException
	{
		Hashtable<String, BlockDef> blocks = new Hashtable<String, BlockDef>();

		//Parse block definitions and put them in the blocks hash table.
		Matcher defsMatcher = Pattern.compile(
			"([a-zA-Z0-9]+)"+      //Group 1: name
			"=([A-Z_]+)"+          //Group 2: material name
			"(?:\\:([A-Z0-9_]+))?"+   //Group 3: optional: direction/meta
			"(?:#([0-9]+))?"       //Group 4: optional: index
		).matcher(defs);
		while (defsMatcher.find())
		{
			String defString = defsMatcher.group(0);
			BlockDef block = new BlockDef();

			try
			{
				block.name = defsMatcher.group(1);
				block.material = Material.valueOf(defsMatcher.group(2));

				if (defsMatcher.group(4) != null)
				{
					block.index = Integer.valueOf(defsMatcher.group(4));
				}

				if (defsMatcher.group(3) != null)
				{
					String dir = defsMatcher.group(3);

					if (dir.equals("FORWARDS"))
						block.face = direction;
					else if (dir.equals("RIGHT"))
						block.face = rotateFace(direction, 1);
					else if (dir.equals("BACK"))
						block.face = rotateFace(direction, 2);
					else if (dir.equals("LEFT"))
						block.face = rotateFace(direction, 3);
					else if (isNumeric(dir))
						block.metadata = (byte)(Integer.parseInt(dir));
					else
						block.face = BlockFace.valueOf(defsMatcher.group(3));
				}

				blocks.put(block.name,  block);
			}
			catch (RuntimeException e)
			{
				throw new RuntimeException("Malformed block definition: "+defString);
			}
		}

		return blocks;
	}

	private void placeBlock(Block worldBlock, BlockDef block)
	{
		if (block.face == null && block.metadata == 0)
		{
			System.out.println("blockface and metadata is nothing");
			worldBlock.setType(block.material);
		}
		else
		{
			//I would prefer to do this without depreciated method and the weird torch hack,
			//but the bukkit/spigot API seems to be buggy. If I set the block type with setType and then
			//set the direction, attachable blocks will for some reason pop off, even if I
			//tell setType to disable physics updates.
			if (block.face != null)
			{
				Torch torch = new Torch();
				torch.setFacingDirection(block.face);
				worldBlock.setTypeIdAndData(block.material.getId(), torch.getData(), true);
			}
			else
			{
				worldBlock.setTypeIdAndData(block.material.getId(), block.metadata, true);
			}
		}
	}

	private void execIteration(int iteration) throws RuntimeException
	{
		//Initiate coordinates
		int x = 0;
		int y = 0;
		int z = 0;

		String longName = "";
		Boolean parsingLongName = false;

		for (int i = 0; i < worldPart.length(); ++i)
		{
			char c = worldPart.charAt(i);

			//Get the block in the world, accounting for the player's location.
			//These values have been found through a lot of experimentation.
			Block worldBlock;
			if (direction == BlockFace.NORTH)
				worldBlock = world.getBlockAt(
					loc.getBlockX() - z,
					loc.getBlockY() + y,
					loc.getBlockZ() - x - 1
				);
			else if (direction == BlockFace.WEST)
				worldBlock = world.getBlockAt(
					loc.getBlockX() - x - 1,
					loc.getBlockY() + y,
					loc.getBlockZ() + z
				);
			else if (direction == BlockFace.SOUTH)
				worldBlock = world.getBlockAt(
					loc.getBlockX() + z,
					loc.getBlockY() + y,
					loc.getBlockZ() + x + 1
				);
			else
				worldBlock = world.getBlockAt(
					loc.getBlockX() + x + 1,
					loc.getBlockY() + y,
					loc.getBlockZ() - z
				);

			if (c == '\'')
			{
				//If we encounter a ' while parsing a long name, that means
				//we have reached the end of the long name.
				if (parsingLongName)
				{
					if (!blockDefs.containsKey(longName))
						throw new Error("Block "+longName+" is not defined.");

					x += 1;
					BlockDef block = blockDefs.get(longName);
					if (block.index == i)
						placeBlock(worldBlock, block);

					longName = "";
					parsingLongName = false;
				}
				//If we encounter a ' while not parsing a long name, that means it's time
				//to start parsing a long name.
				else
				{
					parsingLongName = true;
				}

				continue;
			}
			else if (parsingLongName)
			{
				longName += c;
				continue;
			}

			switch (c)
			{
			case ',':
				x = 0;
				break;
			case ';':
				x = 0;
				z = 0;
				break;
			case ' ':
				x += 1;
				break;
			case '$':    //Using $ to do x -= 1 is usually a bad idea, but
				x -= 1;  //it's useful in some cases
				break;
			case '<':
				z += 1;
				break;
			case '>':
				z -= 1;
				break;
			case '+':
				y += 1;
				break;
			case '-':
				y -= 1;
				break;
			default:
				String s = String.valueOf(c);
				if (!blockDefs.containsKey(s))
					continue;

				x += 1;
				BlockDef block = blockDefs.get(s);

				if (block.index != iteration)
					continue;

				placeBlock(worldBlock, block);
			}
		}
	}

	public void execute() throws RuntimeException
	{
		for (int i = 0; i <= biggestIndex; ++i)
		{
			execIteration(i);
		}
	}
}
