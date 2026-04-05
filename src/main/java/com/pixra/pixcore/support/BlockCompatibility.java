package com.pixra.pixcore.support;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;

import java.lang.reflect.Method;

public final class BlockCompatibility {

    private static final String LEGACY_DATA_PREFIX = "legacy:";

    private BlockCompatibility() {
    }

    public static byte getBlockDataSafe(Block block) {
        if (block == null) {
            return 0;
        }

        try {
            Object value = block.getClass().getMethod("getData").invoke(block);
            if (value instanceof Number) {
                return (byte) ((Number) value).intValue();
            }
        } catch (Throwable ignored) {
        }

        return extractBedData(getModernBlockData(block));
    }

    public static String serializeBlockData(Block block) {
        if (block == null || block.getType() == Material.AIR) {
            return "";
        }

        String modernData = serializeModernBlockData(getModernBlockData(block));
        if (modernData != null && !modernData.isEmpty()) {
            return modernData;
        }

        return LEGACY_DATA_PREFIX + Byte.toUnsignedInt(getBlockDataSafe(block));
    }

    public static byte getBedDataFromSerialized(String serializedData) {
        if (serializedData == null || serializedData.isEmpty()) {
            return 0;
        }

        Byte legacyData = parseLegacyBlockData(serializedData);
        if (legacyData != null) {
            return legacyData;
        }

        return extractBedData(createBlockData(serializedData));
    }

    public static boolean applySerializedBlockData(Block block, String serializedData) {
        if (block == null || serializedData == null || serializedData.isEmpty()) {
            return false;
        }

        Byte legacyData = parseLegacyBlockData(serializedData);
        if (legacyData != null) {
            return setLegacyBlockData(block, legacyData.byteValue());
        }

        Object blockData = createBlockData(serializedData);
        return blockData != null && setModernBlockData(block, blockData, false);
    }

    public static boolean setBlockType(Block block, Material material, boolean applyPhysics) {
        if (block == null || material == null) {
            return false;
        }

        try {
            Method method = block.getClass().getMethod("setType", Material.class, boolean.class);
            method.invoke(block, material, applyPhysics);
            return true;
        } catch (Throwable ignored) {
        }

        try {
            Method method = block.getClass().getMethod("setType", Material.class);
            method.invoke(block, material);
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    public static boolean configureModernBedState(Block foot, Block head, byte footData, byte headData) {
        Object footBlockData = getModernBlockData(foot);
        Object headBlockData = getModernBlockData(head);
        if (footBlockData == null || headBlockData == null) {
            return false;
        }

        try {
            Class<?> bedDataClass = Class.forName("org.bukkit.block.data.type.Bed");
            if (!bedDataClass.isInstance(footBlockData) || !bedDataClass.isInstance(headBlockData)) {
                return false;
            }

            BlockFace[] facingMap = {
                    BlockFace.SOUTH,
                    BlockFace.WEST,
                    BlockFace.NORTH,
                    BlockFace.EAST
            };
            BlockFace footFacing = facingMap[footData & 3];
            BlockFace headFacing = facingMap[headData & 3];

            Method setFacing = bedDataClass.getMethod("setFacing", BlockFace.class);
            setFacing.invoke(footBlockData, footFacing);
            setFacing.invoke(headBlockData, headFacing);

            Class<?> partEnum = Class.forName("org.bukkit.block.data.type.Bed$Part");
            Object footPart = Enum.valueOf((Class<? extends Enum>) partEnum, "FOOT");
            Object headPart = Enum.valueOf((Class<? extends Enum>) partEnum, "HEAD");
            Method setPart = bedDataClass.getMethod("setPart", partEnum);
            setPart.invoke(footBlockData, footPart);
            setPart.invoke(headBlockData, headPart);

            return setModernBlockData(foot, footBlockData, false)
                    && setModernBlockData(head, headBlockData, false);
        } catch (Throwable ignored) {
            return false;
        }
    }

    public static byte extractBedData(Object blockData) {
        if (blockData == null) {
            return 0;
        }

        try {
            Class<?> bedDataClass = Class.forName("org.bukkit.block.data.type.Bed");
            if (!bedDataClass.isInstance(blockData)) {
                return 0;
            }

            int dir = 0;
            Object facing = bedDataClass.getMethod("getFacing").invoke(blockData);
            if (facing != null) {
                String facingName = facing.toString();
                if ("SOUTH".equalsIgnoreCase(facingName)) {
                    dir = 0;
                } else if ("WEST".equalsIgnoreCase(facingName)) {
                    dir = 1;
                } else if ("NORTH".equalsIgnoreCase(facingName)) {
                    dir = 2;
                } else if ("EAST".equalsIgnoreCase(facingName)) {
                    dir = 3;
                }
            }

            boolean isHead = false;
            try {
                Object part = bedDataClass.getMethod("getPart").invoke(blockData);
                isHead = part != null && "HEAD".equalsIgnoreCase(part.toString());
            } catch (Throwable ignored) {
            }

            return (byte) (dir | (isHead ? 8 : 0));
        } catch (Throwable ignored) {
            return 0;
        }
    }

    private static Object getModernBlockData(Block block) {
        if (block == null) {
            return null;
        }

        try {
            Method method = block.getClass().getMethod("getBlockData");
            return method.invoke(block);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static String serializeModernBlockData(Object blockData) {
        if (blockData == null) {
            return "";
        }

        try {
            Object serialized = blockData.getClass().getMethod("getAsString").invoke(blockData);
            return serialized instanceof String ? (String) serialized : "";
        } catch (Throwable ignored) {
            return "";
        }
    }

    private static Object createBlockData(String serializedData) {
        if (serializedData == null || serializedData.isEmpty()) {
            return null;
        }

        try {
            Method method = Bukkit.class.getMethod("createBlockData", String.class);
            return method.invoke(null, serializedData);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static boolean setModernBlockData(Block block, Object blockData, boolean applyPhysics) {
        if (block == null || blockData == null) {
            return false;
        }

        try {
            Class<?> blockDataClass = Class.forName("org.bukkit.block.data.BlockData");
            Method method = block.getClass().getMethod("setBlockData", blockDataClass, boolean.class);
            method.invoke(block, blockData, applyPhysics);
            return true;
        } catch (Throwable ignored) {
        }

        try {
            Class<?> blockDataClass = Class.forName("org.bukkit.block.data.BlockData");
            Method method = block.getClass().getMethod("setBlockData", blockDataClass);
            method.invoke(block, blockData);
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static Byte parseLegacyBlockData(String serializedData) {
        if (serializedData == null || !serializedData.startsWith(LEGACY_DATA_PREFIX)) {
            return null;
        }

        try {
            int value = Integer.parseInt(serializedData.substring(LEGACY_DATA_PREFIX.length()));
            return (byte) (value & 0xFF);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static boolean setLegacyBlockData(Block block, byte data) {
        if (block == null) {
            return false;
        }

        try {
            Method method = block.getClass().getMethod("setData", byte.class);
            method.invoke(block, data);
            return true;
        } catch (Throwable ignored) {
        }

        try {
            Method method = block.getClass().getMethod("setData", byte.class, boolean.class);
            method.invoke(block, data, false);
            return true;
        } catch (Throwable ignored) {
        }

        try {
            Method setTypeIdAndData = Block.class.getMethod("setTypeIdAndData", int.class, byte.class, boolean.class);
            Object typeId = Material.class.getMethod("getId").invoke(block.getType());
            if (typeId instanceof Number) {
                setTypeIdAndData.invoke(block, ((Number) typeId).intValue(), data, false);
                return true;
            }
        } catch (Throwable ignored) {
        }

        return false;
    }
}
