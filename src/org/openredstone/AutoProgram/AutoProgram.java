package org.openredstone.AutoProgram;

import java.util.Hashtable;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.plugin.PluginDescriptionFile;
import org.bukkit.plugin.java.JavaPlugin;

public class AutoProgram extends JavaPlugin implements Listener
{
	public final Logger logger = Logger.getLogger("Minecraft");

	PluginDescriptionFile descFile = this.getDescription();
	
	public void onEnable()
	{
		logger.info(descFile.getName()+" enabled.");
	}
	
	public void onDisable()
	{
		logger.info(descFile.getName()+" disabled.");
	}
	
	public boolean onCommand(CommandSender sender, Command cmd, String commandLabel, String[] args)
	{
		if (!(sender instanceof Player))
		{
			sender.sendMessage("This command can only be run by a player.");
			return true;
		}

		Player player = (Player)sender;
		
		if (cmd.getName().equalsIgnoreCase("autoprogram"))
		{
			try
			{
				execProgString(args[0], player);
			}
			catch (Exception e)
			{
				player.sendMessage(e.getMessage());
			}
		}
		
		return false;
	}
	
	private void execProgString(String prog, Player player) throws Exception
	{
		Hashtable<String, BlockDef> blockDefs = new Hashtable<String, BlockDef>();
		Location loc = player.getLocation();
		World world = loc.getWorld();
		
		//Calculate the player's direction
		int direction = 4 + Math.round(loc.getYaw() % 360/90);
		if (direction > 3)
			direction = 0;
		
		String[] parts = prog.split("\\|");
		
		//We need both the defs and the world part of the program
		if (parts.length != 2)
			throw new Exception("You need to supply both the defs and the world part of the program, separated by |.");
		
		String defsPart = parts[0];
		String worldPart = parts[1];
		
		//This indicates how many times we have to iterate through the program.
		int iterationCount = 1;
		
		//Parse block definitions and put them in the blocks hash table.
		Matcher defsMatcher = Pattern.compile("[a-zA-Z0-9]+=[0-9]+(:(\\+)?[0-9]+)?(#[0-9]+)?").matcher(defsPart);
		while (defsMatcher.find())
		{
			String def = defsMatcher.group(0);
			BlockDef block = new BlockDef();
			
			//We want to make a copy of the definition string which won't be modified.
			//This way, we can give better error messages.
			String origDef = def;
			
			try
			{
				//The part before the = is the block name, so we get that and then
				//prepare the definition string for the next part by removing the block name part.
				block.name = def.split("=")[0];
				def = def.split("=")[1];
				
				//The blockIndex value indicates how 
				if (def.contains("#"))
				{
					block.index = Integer.parseInt(def.split("#")[1]);
					if (iterationCount < block.index + 1)
						iterationCount = block.index + 1;
				}
				
				//The part after the = and before the : is the block ID, so we get that.
				block.blockId = Integer.parseInt(def.split(":")[0]);
				
				//The part after the : is the block metadata, so we get that if it exists.
				if (def.contains(":"))
				{
					String metaPart = def.split(":")[1].split("#")[0];
					if (metaPart.startsWith("+"))
					{
						//If the metadata part starts with a +, that indicates that the block should be rotated relative to the player.
						//+0 should be rotated so that it points to the right of the player, +1 should point the opposite
						//direction of the player, etc.
						//Rotation in minecraft works by applying metadata to the block. These metadata numbers have been found using
						//trial and error, and at least rotate redstone torches correctly.
						metaPart = metaPart.substring(1);
						switch((direction + Integer.parseInt(metaPart)) % 4)
						{
						case 0:
							block.metadata = 2;
							break;
						case 1:
							block.metadata = 4;
							break;
						case 2:
							block.metadata = 1;
							break;
						case 3:
							block.metadata = 3;
							break;
						}
					}
					else
					{
						//If the metadata part doesn't start with a +, just use it directly as the block's metadata.
						block.metadata = (byte)Integer.parseInt(metaPart);
					}
				}
				
				blockDefs.put(block.name, block);
			}
			catch (Exception e)
			{
				throw new Exception("Malformed block definition: "+origDef);
			}
		}
		
		//Now that we have the block definitions, we execute the world part of the program.
		for (int i = 0; i < iterationCount; ++i)
		{
			//Initiate coordinates
			int x = 0;
			int y = 0;
			int z = 0;
			
			for (int j = 0; j < worldPart.length(); ++j)
			{
				char c = worldPart.charAt(j);
				
				
				//Get the block in the world, accounting for the player's location.
				//These values have been found through a lot of experimentation.
				Block worldBlock;
				if (direction == 0)
					worldBlock = world.getBlockAt(
						loc.getBlockX() + z,
						loc.getBlockY() + y,
						loc.getBlockZ() + x + 1
					);
				else if (direction == 1)
					worldBlock = world.getBlockAt(
						loc.getBlockX() - x - 1,
						loc.getBlockY() + y,
						loc.getBlockZ() + z
					);
				else if (direction == 2)
					worldBlock = world.getBlockAt(
						loc.getBlockX() - z,
						loc.getBlockY() + y,
						loc.getBlockZ() - x - 1
					);
				else
					worldBlock = world.getBlockAt(
						loc.getBlockX() + x + 1,
						loc.getBlockY() + y,
						loc.getBlockZ() - z
					);
				
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
					if (blockDefs.containsKey(s))
					{
						x += 1;
						BlockDef block = blockDefs.get(s);
						if (block.index == i)
						{
							//Yes, these methods are depreciated. If anyone knows how to achieve this without
							//depreciated methods, please do tell; I went through the Spigot API javadocs, and couldn't
							//find anything.
							worldBlock.setTypeId(block.blockId);
							worldBlock.setData(block.metadata);
						}
					}
				}
			}
		}
	}
}