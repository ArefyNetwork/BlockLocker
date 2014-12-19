package nl.rutgerkok.chestsignprotect.impl;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nullable;

import nl.rutgerkok.chestsignprotect.ChestSettings;
import nl.rutgerkok.chestsignprotect.ProtectionSign;
import nl.rutgerkok.chestsignprotect.SignParser;
import nl.rutgerkok.chestsignprotect.SignType;
import nl.rutgerkok.chestsignprotect.impl.nms.NMSAccessor;
import nl.rutgerkok.chestsignprotect.impl.profile.ProfileFactoryImpl;
import nl.rutgerkok.chestsignprotect.profile.Profile;

import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.Sign;
import org.bukkit.event.block.SignChangeEvent;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

import com.google.common.base.Optional;

/**
 * Reads a single sign for profiles. Doesn't verify the sign header, the first
 * line is simply skipped for reading.
 *
 */
class SignParserImpl implements SignParser {

    private final ChestSettings chestSettings;
    private final NMSAccessor nms;
    private final ProfileFactoryImpl profileFactory;

    SignParserImpl(ChestSettings chestSettings, NMSAccessor nms,
            ProfileFactoryImpl profileFactory) {
        this.nms = nms;
        this.profileFactory = profileFactory;
        this.chestSettings = chestSettings;
    }

    @Override
    public Optional<SignType> getSignType(Sign sign) {
        String header = sign.getLine(0);
        return Optional.fromNullable(getSignTypeUnsafe(header));
    }

    @Nullable
    private SignType getSignTypeUnsafe(String header) {
        header = ChatColor.stripColor(header).trim();
        for (SignType type : SignType.values()) {
            if (header.equalsIgnoreCase(chestSettings.getSimpleLocalizedHeader(type))) {
                return type;
            }
        }
        return null;
    }

    @Override
    public boolean hasValidHeader(Sign sign) {
        return getSignType(sign).isPresent();
    }

    /**
     * Used for signs where the hidden text was found.
     *
     * @param jsonArray
     *            The hidden text.
     * @param addTo
     *            The profile collection to add all profiles to.
     * @return
     */
    private Optional<ProtectionSign> parseAdvancedSign(Location location, String header, List<JSONObject> list) {
        SignType signType = getSignTypeUnsafe(header);
        if (signType == null) {
            return Optional.absent();
        }

        List<Profile> profiles = new ArrayList<Profile>();
        for (JSONObject object : list) {
            Optional<Profile> profile = profileFactory.fromSavedObject(object);
            if (profile.isPresent()) {
                profiles.add(profile.get());
            }
        }

        return Optional.<ProtectionSign> of(new ProtectionSignImpl(location, signType, profiles));
    }

    /**
     * Used for signs where the hidden information was never written or was
     * lost.
     *
     * @param lines
     *            The lines on the sign.
     * @return The protection sign, if the header format is correct.
     */
    private Optional<ProtectionSign> parseSimpleSign(Location location, String[] lines) {
        SignType signType = getSignTypeUnsafe(lines[0]);
        if (signType == null) {
            return Optional.absent();
        }

        List<Profile> profiles = new ArrayList<Profile>();
        for (int i = 1; i < lines.length; i++) {
            String name = lines[i].trim();
            if (!name.isEmpty()) {
                profiles.add(profileFactory.fromDisplayText(name));
            }
        }

        return Optional.<ProtectionSign> of(new ProtectionSignImpl(location, signType, profiles));
    }

    @Override
    public Optional<ProtectionSign> parseSign(Sign sign) {
        Optional<List<JSONObject>> foundTextData = nms.getJsonData(sign);
        if (foundTextData.isPresent()) {
            System.out.println("Found extra data");
            return parseAdvancedSign(sign.getLocation(), sign.getLine(0), foundTextData.get());
        } else {
            System.out.println("Found simple sign");
            return parseSimpleSign(sign.getLocation(), sign.getLines());
        }
    }

    @SuppressWarnings("unchecked")
    @Override
    public void saveSign(ProtectionSign sign) {
        // Find sign
        Location signLocation = sign.getLocation();
        Block block = signLocation.getWorld().getBlockAt(signLocation);
        BlockState blockState = block.getState();
        if (!(blockState instanceof Sign)) {
            return;
        }

        // Update sign, both visual and using raw JSON
        Sign signState = (Sign) blockState;
        signState.setLine(0, chestSettings.getFancyLocalizedHeader(sign.getType()));

        JSONArray jsonArray = new JSONArray();
        int i = 1; // Start at 1 to avoid overwriting the header
        for (Profile profile : sign.getProfiles()) {
            signState.setLine(i, profile.getDisplayName());
            jsonArray.add(profile.getSaveObject());
            i++;
        }

        // Save the text and JSON
        // (JSON after text, to avoid text overwriting the JSON)
        signState.update();
        nms.setJsonData(signState, jsonArray);
    }

    @Override
    public Optional<SignType> getSignType(SignChangeEvent event) {
        return Optional.fromNullable(getSignTypeUnsafe(event.getLine(0)));
    }

}
