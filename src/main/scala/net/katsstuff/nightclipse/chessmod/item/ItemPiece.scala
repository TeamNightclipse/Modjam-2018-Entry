package net.katsstuff.nightclipse.chessmod.item

import java.util

import scala.collection.JavaConverters._

import net.katsstuff.mirror.client.helper.Tooltip
import net.katsstuff.nightclipse.chessmod.block.BlockPiece
import net.katsstuff.nightclipse.chessmod.{ChessItems, ChessNames, Piece, PieceColor, PieceType}
import net.minecraft.advancements.CriteriaTriggers
import net.minecraft.block.Block
import net.minecraft.block.state.IBlockState
import net.minecraft.client.util.ITooltipFlag
import net.minecraft.creativetab.CreativeTabs
import net.minecraft.entity.player.{EntityPlayer, EntityPlayerMP}
import net.minecraft.item.{ItemBlock, ItemStack}
import net.minecraft.nbt.NBTTagCompound
import net.minecraft.util.math.BlockPos
import net.minecraft.util.{EnumActionResult, EnumFacing, EnumHand, NonNullList, SoundCategory}
import net.minecraft.world.World

class ItemPiece extends ItemChessBase(ChessNames.Items.Piece) {
  setHasSubtypes(true)
  setMaxDamage(0)

  override def getSubItems(tab: CreativeTabs, items: NonNullList[ItemStack]): Unit = {
    val allPieces = for {
      tpe   <- PieceType.all
      color <- PieceColor.all
    } yield Piece(tpe, color)

    items.addAll(allPieces.map(ItemPiece.stackOf).asJava)
  }

  //Mostly copy pasted from ItemBlock with a few changes
  override def onItemUse(
      player: EntityPlayer,
      worldIn: World,
      pos: BlockPos,
      hand: EnumHand,
      facing: EnumFacing,
      hitX: Float,
      hitY: Float,
      hitZ: Float
  ): EnumActionResult = {
    val itemstack = player.getHeldItem(hand)

    val iblockstate = worldIn.getBlockState(pos)
    val block       = iblockstate.getBlock

    if (!itemstack.isEmpty && player.canPlayerEdit(pos, facing, itemstack) && worldIn.mayPlace(
          block,
          pos,
          false,
          facing,
          null
        )) {
      val Piece(tpe, color) = ItemPiece.pieceOf(itemstack)
      val relevantBlock     = BlockPiece.ofPieceType(tpe)

      val posWithOffset = if (!block.isReplaceable(worldIn, pos)) pos.offset(facing) else pos

      val i = this.getMetadata(itemstack.getMetadata)
      var iblockstate1 = relevantBlock
        .getStateForPlacement(worldIn, pos, facing, hitX, hitY, hitZ, i, player, hand)
        .withProperty(BlockPiece.White, color == PieceColor.White)

      if (placeBlockAt(relevantBlock, itemstack, player, worldIn, pos, facing, hitX, hitY, hitZ, iblockstate1)) {
        iblockstate1 = worldIn.getBlockState(pos)
        val soundType = iblockstate1.getBlock.getSoundType(iblockstate1, worldIn, pos, player)
        worldIn.playSound(
          player,
          pos,
          soundType.getPlaceSound,
          SoundCategory.BLOCKS,
          (soundType.getVolume + 1.0F) / 2.0F,
          soundType.getPitch * 0.8F
        )
        itemstack.shrink(1)
      }
      EnumActionResult.SUCCESS
    } else EnumActionResult.FAIL
  }

  /**
    * Called to actually place the block, after the location is determined
    * and all permission checks have been made.
    *
    * @param stack The item stack that was used to place the block. This can be changed inside the method.
    * @param player The player who is placing the block. Can be null if the block is not being placed by a player.
    * @param side The side the player (or machine) right-clicked on.
    */
  def placeBlockAt(
      relevantBlock: Block,
      stack: ItemStack,
      player: EntityPlayer,
      world: World,
      pos: BlockPos,
      side: EnumFacing,
      hitX: Float,
      hitY: Float,
      hitZ: Float,
      newState: IBlockState
  ): Boolean = {
    if (!world.setBlockState(pos, newState, 11)) false
    else {
      val state = world.getBlockState(pos)
      if (state.getBlock == relevantBlock) {
        ItemBlock.setTileEntityNBT(world, player, pos, stack)
        relevantBlock.onBlockPlacedBy(world, pos, state, player, stack)
        player match {
          case p: EntityPlayerMP => CriteriaTriggers.PLACED_BLOCK.trigger(p, pos, stack)
          case _                 =>
        }
      }
      true
    }
  }

  override def getUnlocalizedName(stack: ItemStack): String = {
    val Piece(tpe, color) = ItemPiece.pieceOf(stack)
    s"$getUnlocalizedName.${tpe.name}.${color.name}"
  }

  override def addInformation(
      stack: ItemStack,
      worldIn: World,
      tooltip: util.List[String],
      flagIn: ITooltipFlag
  ): Unit = {
    val Piece(tpe, color) = ItemPiece.pieceOf(stack)
    val seperator         = " : "

    // @formatter:off
    Tooltip
      .addI18n("piece.type").add(seperator).addI18n(s"piece.type.${tpe.name}").newline
      .addI18n("piece.color").add(seperator).addI18n(s"piece.color.${color.name}").newline
      .addI18n("piece.max").add(seperator).add(tpe.max).newline
      .build(tooltip)
    // @formatter:on
  }
}
object ItemPiece {

  val NbtType    = "type"
  val NbtIsWhite = "white"

  def pieceOf(stack: ItemStack): Piece = {
    val compound = stack.getTagCompound
    if (compound == null) Piece(PieceType.Pawn, PieceColor.Black)
    else {
      val tpe   = PieceType.fromId(compound.getByte(NbtType)).get
      val color = if (compound.getBoolean(NbtIsWhite)) PieceColor.White else PieceColor.Black
      Piece(tpe, color)
    }
  }

  def stackOf(piece: Piece): ItemStack = {
    val stack    = new ItemStack(ChessItems.Piece)
    val compound = new NBTTagCompound

    compound.setByte(NbtType, PieceType.idOf(piece.tpe))
    compound.setBoolean(NbtIsWhite, piece.color == PieceColor.White)
    stack.setTagCompound(compound)

    stack
  }
}
