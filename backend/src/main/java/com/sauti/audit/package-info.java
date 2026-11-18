/**
 * Activity log: an append-only record of security-relevant changes in workspaces and accounts.
 * Other modules never call it; it listens to the events they publish, so they stay unaware of it.
 */
package com.sauti.audit;
