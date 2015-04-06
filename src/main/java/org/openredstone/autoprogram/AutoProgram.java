package org.openredstone.autoprogram;

import java.io.File;
import java.util.Hashtable;
import java.util.Scanner;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.plugin.PluginDescriptionFile;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.Material;
import org.bukkit.material.Torch;

public class AutoProgram extends JavaPlugin implements Listener
{
	public final Logger logger = Logger.getLogger("Minecraft");
	public final PluginDescriptionFile descFile = this.getDescription();
	private final String progPath = this.getConfig().getString("progPath");
	
	public void onEnable()
	{
		this.saveDefaultConfig();
		logger.info(descFile.getName()+" enabled.");
		
		File file = new File(progPath);
		logger.info(descFile.getName()+"'s path is: "+file.getAbsolutePath());
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
			if (args.length != 1)
				return true;
			
			if (!Pattern.compile("^[a-zA-Z0-9_]+$").matcher(args[0]).find())
			{
				player.sendMessage("The program name contains illegal characters.");
				return true;
			}
			
			if (!Pattern.compile("^[a-zA-Z0-9_]+$").matcher(player.getName()).find())
			{
				player.sendMessage("Your username contains illegal characters.");
				return true;
			}
			
			try
			{
				Scanner file = new Scanner(new File(progPath+"/"+player.getName()+"/"+args[0]+".prog"));
				String prog = "";
				while (file.hasNextLine()) prog += file.nextLine();
				file.close();
				execProgString(prog, player);
			}
			catch (Exception e)
			{
				player.sendMessage(e.getMessage());
			}
		}
		return false;
	}
	
	private BlockFace rotateFace(BlockFace face, int n) throws Exception
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
				throw new Exception("That face isn't implemented yet.");
			}
		}
		
		return face;
	}
	
	private void placeBlock(Block worldBlock, BlockDef block)
	{
		if (block.face == null)
		{
			worldBlock.setType(block.material);
		}
		else
		{
			//Used to calculate the data required for rotating
			Torch torch = new Torch();
			torch.setFacingDirection(block.face);
			
			//I would prefer to do this without depreciated method and the weird torch hack,
			//but the bukkit/spigot API seems to be buggy. If I set the block type with setType and then
			//set the direction, attachable blocks will for some reason pop off, even if I
			//tell setType to disable physics updates.
			worldBlock.setTypeIdAndData(block.material.getId(), torch.getData(), true);
		}		
	}
	
	private void execProgString(String prog, Player player) throws Exception
	{
		Hashtable<String, BlockDef> blockDefs = new Hashtable<String, BlockDef>();
		Location loc = player.getLocation();
		World world = loc.getWorld();
		
		//Calculate the player's direction
		BlockFace direction;
		
		//yaw is very strange and not consistent. Therefore, we do this to make it consistently between 0 and 360.
		
		float yaw = ((loc.getYaw() % 360) + 360) % 360;
		if (yaw > 135 && yaw < 225)
			direction = BlockFace.NORTH;
		else if (yaw > 225 && yaw < 315)
			direction = BlockFace.EAST;
		else if (yaw > 315 || yaw < 45)
			direction = BlockFace.SOUTH;
		else
			direction = BlockFace.WEST;
				
		String[] parts = prog.split("\\|");
		
		//We need both the defs and the world part of the program
		if (parts.length != 2)
			throw new Exception("You need to supply both the defs and the world part of the program, separated by |.");
		
		String defsPart = parts[0];
		String worldPart = parts[1];
		
		//This indicates how many times we have to iterate through the program.
		int iterationCount = 1;
		
		//Parse block definitions and put them in the blocks hash table.
		Matcher defsMatcher = Pattern.compile(
			"([a-zA-Z0-9]+)"+      //Group 1: name
			"=([A-Z_]+)"+          //Group 2: material name
			"(?:\\:([A-Z_]+))?"+   //Group 3: optional: direction
			"(?:#([0-9]+))?"       //Group 4: optional: index
		).matcher(defsPart);
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
					if (iterationCount < block.index + 1)
						iterationCount = block.index + 1;
				}
				
				if (defsMatcher.group(3) != null)
				{
					switch (defsMatcher.group(3))
					{
					case "FORWARDS":
						block.face = direction;
						break;
					case "RIGHT":
						block.face = rotateFace(direction, 1);
						break;
					case "BACK":
						block.face = rotateFace(direction, 2);
						break;
					case "LEFT":
						block.face = rotateFace(direction, 3);
						break;
					default:
						block.face = BlockFace.valueOf(defsMatcher.group(3));
						break;
					}
				}
				blockDefs.put(block.name,  block);
			}
			catch (Exception e)
			{
				throw new Exception("Malformed block definition: "+defString);
			}
		}
		
		//Now that we have the block definitions, we execute the world part of the program.
		for (int i = 0; i < iterationCount; ++i)
		{
			//Initiate coordinates
			int x = 0;
			int y = 0;
			int z = 0;
			
			String longName = "";
			Boolean parsingLongName = false;
			
			for (int j = 0; j < worldPart.length(); ++j)
			{
				char c = worldPart.charAt(j);
				
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
					
					if (block.index != i)
						continue;

					placeBlock(worldBlock, block);
				}
			}
		}
	}
}
