package com.shopsync.mini.data;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.logging.Logger;

/**
 * Saves any Serializable object to a file and loads it back (Lecture 9).
 * One class is reused for the search history, the cart and the budget.
 *
 * Concepts: generic class {@code <T extends Serializable>} (like ArrayList<T>),
 * ObjectOutputStream / ObjectInputStream, casting, multi-catch.
 */
public class SerialStore<T extends Serializable> {

    private static final Logger LOG = Logger.getLogger(SerialStore.class.getName());
    private final File file;

    public SerialStore(File file) {
        this.file = file;
    }

    /** Returns the saved object, or {@code fallback} if there is no file yet (or it is unreadable). */
    @SuppressWarnings("unchecked")
    public T load(T fallback) {
        if (!file.exists()) {
            LOG.info(file.getName() + " not found, starting fresh");
            return fallback;
        }
        try (ObjectInputStream in = new ObjectInputStream(new FileInputStream(file))) {
            T value = (T) in.readObject();
            LOG.info("Loaded " + file.getName());
            return value;
        } catch (IOException | ClassNotFoundException | ClassCastException e) {
            LOG.warning("Could not read " + file.getName() + ", starting fresh: " + e.getMessage());
            return fallback;
        }
    }

    public void save(T value) {
        try (ObjectOutputStream out = new ObjectOutputStream(new FileOutputStream(file))) {
            out.writeObject(value);
        } catch (IOException e) {
            LOG.warning("Could not save " + file.getName() + ": " + e.getMessage());
        }
    }
}
