package chip;

import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Random;

public class Chip {
	
	/**
	 *  4kB of 8-bit memory
	 *  At position 0x50: The "BIOS" fontset
	 *  At position 0x200: The start of every program
	 */
	public char[] memory;
	public char[] V; 					//Register
	public char I; 						//Address Pointer
	private char pc; 					//Program Counter
	
	private char stack[]; 			//Subroutine callstack. Allows up to 16 levels of nesting.
	private int sp; 					//Stack Pointer. Points to the next free slot in the stack.
	
	private int delayTimer; 		//Timer used to delay events in programs
	private int soundTimer; 	//Timer used to make beeping sound
	
	private byte[] keys; 			//Array will be our keyboard state
	
	private byte[] display; 		//64x32 pixel monochrome display
	
	public boolean needRedraw;
	
	public void init() {
		memory = new char[4096];
		display = new byte[64 * 32];
		V = new char[16];
		I = 0x0;
		pc = 0x200;
		
		stack = new char[16]; 	//16 level stack
		sp = 0;
		
		delayTimer = 0;
		soundTimer =0;
		
		keys = new byte[16];
		
		needRedraw = false;
		loadFontset();
	}
	
	/**
	 * Executes a single Operation Code / Opcode
	 */
	public void run() {
		//Fetch Opcode
		char opcode = (char) ((memory[pc] << 8) | memory[pc + 1]);
		System.out.print(Integer.toHexString(opcode)+": ");
		//Decode Opcode
		switch(opcode & 0xF000) {
		
		case 0x0000: //Multicase
			switch(opcode & 0x00FF) {
			case 0x00E0: //00E): Clear tha screen
				for(int i = 0; i < display.length; i++) {
					display[i] = 0;
				}
				pc  += 2;
				needRedraw = true;
				break;
			
			case 0x00EE: //00EE: Returns from subroutine
				sp--;
				pc = (char)(stack[sp] + 2);
				System.out.println("Return to "+ Integer.toHexString(pc).toUpperCase());
				break;
				
			default: //0NNN: Calls RCA 1802 Program at address NNN
				System.err.println("Unsupported Opcode!");
				System.exit(0);
				break;
			}
			break;
				
		case 0x1000: { //1NNN: Jumps to address NNN
			int nnn = opcode & 0x0FFF;
			pc = (char)nnn;
			System.out.println("Jumping to " + Integer.toHexString(pc).toUpperCase());
			break;
		}
			
		case 0x2000: //2NNN: Calls subroutine at NNN
			stack[sp] = pc;
			sp++;
			pc = (char) (opcode & 0x0FFF);
			System.out.println("Calling " + Integer.toHexString(pc).toUpperCase() + " from " + Integer.toHexString(stack[sp -1]).toUpperCase());
			break;
			
		case 0x3000: { //3XNN: Skips the next instruction if VX equals NN
			int x = (opcode & 0x0F00) >> 8;
			int nn = (opcode & 0x00FF);
			if(V[x] == nn) {
				pc += 4;
				System.out.println("Skipping next instruction (V["+ x + "] == " + nn+ ")");
			} else {
				pc+=2;
				System.out.println("Not skipping next instruction (V["+ x + "] != " + nn+ ")");
			}
			break;
		}
		
		case 0x4000: { //4XNN: Skip the next instruction if VX != NN
			int x = (opcode & 0x0F00) >> 8;
			int nn = (opcode & 0x00FF);
			if(V[x] != nn) {
				System.out.println("Skipping next instruction (V["+ x + "] != " + nn+ ")");
				pc+=4;
			} else {
				System.out.println("Not skipping next instruction (V["+ x + "] == " + nn+ ")");
				pc+=2;
			}
			break;
		}
		
		case 0x5000: { //5XY0: Skips the next instruction of VX = VY
			int x = (opcode & 0x0F00) >> 8;
			int y = (opcode & 0x0F00) >> 4;
			if(V[x] == V[y]) {
				System.out.println("Skipping next instruction (V["+ x + "] == V[" + y+ "])");
				pc+=4;
			} else {
				System.out.println("Not skipping next instruction (V["+ x + "] != V[" + y+ "])");
				pc+=2;
			}
			break;
		}
		
		case 0x6000: { //6XNN: Sets VX to NN
			int x = (opcode & 0x0F00) >> 8;
			V[x] = (char) (opcode & 0x00FF);
			pc += 2;
			System.out.println("Setting V[" + x + "] to " + (int)V[x]);
			break;
		}	
		
		case 0x7000: { //7XNN: Adds VX to NN
			int x = (opcode & 0x0F00) >> 8;
			int nn = (opcode & 0x00FF);
			V[x] = (char)((V[x] + nn) & 0xFF);
			pc += 2;
			System.out.println("Adding V[" + nn + "] to V[" + "x"+ "] = " + (int)V[x]);
			break;
		}
		
		case 0x8000: //Contains more data in last nibble
			switch(opcode & 0x000F) {
			case 0x0000: { //8XY0: Sets VX to the value of VY
				int x = (opcode & 0x0F00) >> 8;
				int y = (opcode & 0x00F0) >> 4;
				System.out.println("Setting V["+x+"] to "+(int)V[y]);
				V[x] = V[y];
				pc+=2;
				break;
			}
			
			case 0x001: { //8XY1: Sets VX to VX OR VY
				int x = (opcode & 0x0F00) >> 8;
				int y = (opcode & 0x00F0) >> 4;
				System.out.println("Setting V["+x+"] = "+ "V["+ x+"] | V["+y+"]");
				V[x] = (char)((V[x] | V[y]) & 0xFF);
				pc+=2;
				break;
			}
			
			case 0x002: { //8XY2: Sets VX to VX AND VY
				int x = (opcode & 0x0F00) >> 8;
				int y = (opcode & 0x00F0) >> 4;
				System.out.println("Setting V["+x+"] = "+ "V["+ x+"] & V["+y+"]");
				V[x] = (char)(V[x] & V[y]);
				pc+=2;
				break;
			}
			
			case 0x003: { //8XY3: Sets VX to VX XOR XY
				int x = (opcode & 0x0F00) >> 8;
				int y = (opcode & 0x00F0) >> 4;
				System.out.println("Setting V["+x+"] = "+ "V["+ x+"] ^ V["+y+"] (XOR operation)");
				V[x] = (char)((V[x] ^ V[y]) & 0xFF);
				pc+=2;
				break;
			}
			
			case 0x004: { //Adds VY to VX. VF is set to 1 when carry applies else to 0
				int x = (opcode & 0x0F00) >> 8;
				int y = (opcode & 0x00F0) >> 4;
				System.out.println("Adding V["+x+"] ( "+ (int)V[x] + "to V["+ y+"]  ("+ (int)V[y] +") = " + ((V[x] + V[y]) & 0xFF)+", ");
				if(V[y] > 0xFF - V[x]) {
					V[0xF] = 1;
					System.out.println("Carry!");
				} else {
					V[0xF] = 0;
					System.out.println("No Carry!");
				}
				V[x] = (char)((V[x]+V[y]) & 0xFF);
				pc+=2;
				break;
			}
			
			case 0x005: { //VY is subtracted from VX. VF is set to 0 when there is a borrow else 1
				int x = (opcode & 0x0F00) >> 8;
				int y = (opcode & 0x00F0) >> 4;
				System.out.println("V["+x+"] =  "+ (int)V[x] + " V["+ y+"]  "+ (int)V[y] +") = " + (int)V[y] +", ");
				if(V[x] > V[y]) {
					V[0xF] = 1;
					System.out.println("No Borrow");
				} else {
					V[0xF] = 0;
					System.out.println("Borrow");
				}
				V[x] = (char)((V[x]-V[y]) & 0xFF);
				pc+=2;
				break;
			}
			
			case 0x006: { //8XY6: Shift VX right by 1. VF is set to the LSB of VX
				int x = (opcode & 0x0F00) >> 8;
				V[0xF] = (char)(V[x] & 0x1);
				V[x] = (char)(V[x] >> 1);
				pc+=2;
				System.out.println("Shift V[" + x + "] >> 1 and Setting VF to LSB of VX");
				break;
			}
			
			case 0x007: { //8XY7: Sets VX to VY minus VX. VF is set to 0 when there is a borrow, else VF set to 1
				int x = (opcode & 0x0F00) >> 8;
				int y = (opcode & 0x00F0) >> 4;
				if(V[x] > V[y])
					V[0xF] = 0;
				else
					V[0xF] = 1;
				V[x] = (char)((V[y] - V[x]) & 0xFF);
				System.out.println("V[" + x + "] = V[" + y + "] - V[" + x+ "], Applies Borrow if needed");
				pc+=2;
				break;
			}
			
			case 0x00E: { //8XYE: Shifts VX left by 1. VF is set to the value of the MSB of VX before bitshift
				int x = (opcode & 0x0F00) >> 8;
				V[0xF] = (char)(V[x] & 0x80);
				V[x] = (char)(V[x] << 1);
				pc+=2;
				System.out.println("Shift V[" + x + "] << 1 and Setting VF to MSB of VX");
				break;
			}
				default:
					System.err.println("Unsupported Opcode!");
					System.exit(0);
					break;
			}
			break;
		
		case 0x9000: { //9XY0: Skips the next instruction if VX doesn't equal VY
			int x = (opcode & 0x0F00) >> 8;
			int y = (opcode & 0x00F0) >> 4;
			if(V[x] != V[y]) {
				System.out.println("Skipping next instruction V[" + x + "] != V[" + y + "]");
				pc+=4;
			} else {
				System.out.println("Not skipping next instruction V[" + x + "] = V[" + y + "]");
				pc+=2;
			}
			break;
		}
			
		case 0xA000: //ANNN: Set I to NNN
			I = (char) (opcode & 0x0FFF);
			pc += 2;
			System.out.println("Set I to "+ Integer.toHexString(I).toUpperCase());
			break;
		
		case 0xB000: { //BNNN: Jumps to the address of NNN + V0
			int nnn = opcode & 0x0FFF;
			int extra = V[0] & 0xFF;
			pc = (char)(nnn + extra);
			break;
		}
		
		case 0xC000: { //CXNN: Set VS to a random number and NN
			int x = (opcode & 0x0F00) >> 8;
			int nn = (opcode & 0x00FF);
			int RNG = new Random().nextInt(255) & nn;
			System.out.println("V[" + x + "] has been set to (randomized) " + RNG);
			V[x] = (char)RNG;
			pc+=2;
			break;
		}
		
		case 0xD000: { //DXYN: Draw a sprite (X,Y) size (8,N). Sprite is located at I
			int x = V[(opcode & 0x0F00) >> 8];
			int y = V[(opcode & 0x00F0) >> 4];
			int height = opcode & 0x000F;
			V[0xF] = 0;
			for(int dy =0; dy < height; dy++) {
				int line = memory[I+dy];
				for(int dx =0; dx < height; dx++) {
					int pixel = line & (0x80 >> dx);
					if(pixel != 0) {
						int totalX = x+dx;
						int totalY = y+dy;
						totalX = totalX % 64;
						totalY = totalY % 32;
						int index = totalY*64 + totalX;
						
						if(display[index] == 1)
							V[0xF] = 1;
						display[index] ^=1;
					}
				}
			}
			pc += 2;
			needRedraw = true;
			System.out.println("Drawing at V[" + ((opcode & 0x0F00) >> 8) + "] = " + x + ", V["+((opcode & 0x00F0) >> 4) + "] = " + y);
			break;
		}
		
		case 0xE000: {
			switch(opcode & 0x00FF) {
			case 0x009E: { //EX9E: Skip the next instruction of Key VX is pressed
				int x = (opcode & 0x0F00) >> 8;
				int key = V[x];
				if(keys[key] == 1)
					pc +=4;
				else
					pc+=2;
				System.out.println("Skipping next instruction if V["+ x + "] = " + ((int)V[x])+" is pressed");
				break;
			}
			
			case 0x00A1: { //EXA1: Skip the next instruction if the Key VX is NOT pressed
				int x = (opcode & 0x0F00) >> 8;
				int key = V[x];
				if(keys[key] == 0)
					pc +=4;
				else
					pc+=2;
				System.out.println("Skipping next instruction if V["+ x + "] = " + ((int)V[x])+" is not pressed");
				break;
			}
				default:
					System.err.println("Unexisting opcode");
					System.exit(0);
					return;
			}
			break;
		}
		
		case 0xF000:
			switch(opcode & 0x00FF) {
			case 0x0007: { //FX07: Set VX to the value of delayTimer
				int x = (opcode & 0x0F00) >> 8;
				V[x] = (char)delayTimer;
				pc+=2;
				System.out.println("V[" + x + "] has been set to " + delayTimer);
				break;
			}
			
			case 0x000A: { //FX0A: A key is awaited, and then stored in VX
				int x = (opcode & 0x0F00) >> 8;
				for(int i=0; i<keys.length;i++) {
					if(keys[i]==1) {
						V[x] = (char)i;
						pc+=2;
						break;
					}
				}
				System.out.println("Awaiting key press to be stored in V[" + x + "]");
				break;
			}
			
			case 0x0015: { //FX15: Set delayTimer to VX
				int x = (opcode & 0x0F00) >> 8;
				delayTimer = V[x];
				pc+=2;
				System.out.println("Set delayTimer to V[" + x + "] = " + (int)V[x]);
				break;
			}
			
			case 0x0018: { //FX18: Set the sound timer to VX
				int x = (opcode & 0x0F00) >> 8;
				soundTimer = V[x];
				pc+=2;
				break;
			}
			
			case 0x001E: { //FX1E: Adds VX to I
				int x = (opcode & 0x0F00) >> 8;
				I = (char)(I + V[x]);
				System.out.println("Adding V[" + x + "] = " + (int)V[x] + " to I");
				pc+=2;
				break;
			}
			
			case 0x0029: { //FX29: Sets I to the location of the sprite for the character VX (fontset)
				int x = (opcode & 0x0F00) >> 8;
				int character = V[x];
				I = (char)(0x050 + (character * 5));
				System.out.println("Setting I to Character V[" + x + "] = " + (int)V[x] + " Offset to 0x " + Integer.toHexString(I).toUpperCase());
				pc+=2;
				break;
			}
			
			case 0x0033: { //FX33: Store a binary-coded decimal value VX in I, I+1,I+2
				int x = (opcode & 0x0F00) >> 8;
				int value = V[x];
				int hundreds = (value - (value % 100)) / 100;
				value -= hundreds*100;
				int tens = (value - (value % 10)) / 10;
				value -= tens * 10;
				memory[I] = (char)hundreds;
				memory[I+1] = (char)tens;
				memory[I+2] = (char)value;
				System.out.println("Storing Binary-Coded Decimal V[" + x + "] = " + (int)(V[opcode & 0x0F00 >> 8]) + "as {" + hundreds + ", " + tens + ", " + value + "}");
				pc+=2;
				break;
			}
			
			case 0x0055: { //FX55: Stores V0 to VX in memory starting at address I
				int x = (opcode & 0x0F00) >> 8;
				for(int i=0; i<=x; i++)
					memory[I + i] = V[i];
				System.out.println("Setting Memory [" + Integer.toHexString(I & 0xFFFF).toUpperCase() + " + n] = V[0] to V[x]");
				pc+=2;
				break;
			}
			
			case 0x0065: { //FX65: Fills V0 to VX with values from I
				int x = (opcode & 0x0F00) >> 8;
				for(int i=0; i<=x; i++)
					memory[I + i] = V[i];
				System.out.println("Setting V[0] to V[" + x + "] to the values from memory [0x" + Integer.toHexString(I & 0xFFFF).toUpperCase() + "]");
				I = (char)(I + x + 1);
				pc+=2;
				break;
			}
			
			default:
				System.err.println("Unsupported Upcode!");
				System.exit(0);
			}
			break;
		
			default:
				System.err.println("Unsupported Upcode!");
				System.exit(0);
		}
		
		if(soundTimer > 0) {
			soundTimer--;
		}
		if(delayTimer > 0)
			delayTimer--;
	}
	
	/**
	 * The following are functions that are not related to the Opcodes
	 * also namjeff
	 * */
	
	/**
	 * Returns the display data
	 * @return
	 * Current state of the 64x32 dsiplay
	 */
	public byte[] getDisplay() {
		return display;
	}
	
	/**
	 * Checks if a redraw is needed
	 * @return
	 * If a redraw is needed
	 */
	public boolean needsRedraw() {
		return needRedraw;
	}
	
	/**
	 * Notify Chip that is has been redrawn
	 */
	public void removeDrawFlag() {
		needRedraw = false;
	}
	
	/**
	 * Loads the program into the memory
	 * @param file
	 * The location of the program
	 */
	public void loadProgram(String file) {
		DataInputStream input = null;
		try {
			input = new DataInputStream(new FileInputStream(new File(file)));
			int offset = 0;
			while(input.available() > 0) {
				memory[0x200+offset] = (char)(input.readByte() & 0xFF);
				offset++;
			}
		}catch (IOException e) {
			e.printStackTrace();
			System.exit(0);
		} finally {
			if(input!=null) {
				try {input.close();	} catch(IOException e) {}		
			}
		}
	}
	
	/**
	 * Loads the fontset into the memory
	 */
	public void loadFontset() {
		for(int i = 0; i < ChipData.fontset.length; i++)
			memory[0x50 + i] =(char)(ChipData.fontset[i] & 0xFF);
	}
	
	public void setKeyBuffer(int[] keyBuffer) {
		for(int i=0; i<keys.length;i++)
			keys[i] = (byte)keyBuffer[i];
	}
}
