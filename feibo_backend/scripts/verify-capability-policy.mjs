import assert from 'node:assert/strict';
import { canAccessAdminRoute, getAdminRouteRequirement } from '../src/config/adminRoutePolicy.ts';

const admin = (...permissionCodes) => ({
  isAdmin: true,
  isSuperAdmin: false,
  permissionCodes,
});
const superAdmin = {
  isAdmin: true,
  isSuperAdmin: true,
  permissionCodes: [],
};

assert.equal(canAccessAdminRoute('/dashboard', admin('admin.console')), true);
assert.equal(canAccessAdminRoute('/dashboard', admin()), true);
assert.equal(canAccessAdminRoute('/', admin()), true);
assert.equal(canAccessAdminRoute('/dashboard', {
  isAdmin: false,
  isSuperAdmin: false,
  permissionCodes: ['admin.console'],
}), false);
assert.equal(canAccessAdminRoute('/users/list', admin('user.read')), true);
assert.equal(canAccessAdminRoute('/users/list', admin('admin.console')), false);
assert.equal(canAccessAdminRoute('/functions/copywriting/categories', admin('content.manage')), true);

// Original product boundary remains role-constrained even if an ordinary
// administrator is accidentally assigned a sensitive capability.
assert.equal(canAccessAdminRoute('/users/manage', admin('user.manage')), false);
assert.equal(canAccessAdminRoute('/orders/list', admin('admin.console')), false);
assert.equal(canAccessAdminRoute('/orders/list', admin('finance.manage')), false);
assert.equal(canAccessAdminRoute('/orders/list', superAdmin), true);
assert.equal(canAccessAdminRoute('/accounts/roles', admin('identity.manage')), false);
assert.equal(canAccessAdminRoute('/system/info', superAdmin), true);

// GEO is released only to the platform super administrator. The same policy
// drives both menu filtering and the direct-route guard.
assert.equal(canAccessAdminRoute('/geo/workbench', admin('admin.console')), false);
assert.equal(canAccessAdminRoute('/geo/workbench', superAdmin), true);

// Disabled modules are denied even to the hierarchy fallback.
assert.equal(canAccessAdminRoute('/market/report', superAdmin), false);
assert.equal(canAccessAdminRoute('/finance/report', superAdmin), false);

// Retired pages cannot be recovered through bookmarks, case differences,
// encoded paths or descendants, even by a super administrator.
const retiredPages = [
  '/settings/site', '/settings/theme', '/system/payment', '/system/menu',
  '/system/storage/workbench', '/functions/copywriting/questions',
];
for (const path of retiredPages) {
  for (const variant of [path, path + '/', path + '/edit', path.toUpperCase(), path.replaceAll('/', '%2F')]) {
    assert.equal(getAdminRouteRequirement(variant), 'disabled', variant);
    assert.equal(canAccessAdminRoute(variant, superAdmin), false, variant);
    assert.equal(canAccessAdminRoute(variant, admin('admin.console', 'content.manage', 'system.manage')), false, variant);
  }
}
assert.equal(canAccessAdminRoute('/functions/copywriting/categories', superAdmin), true);
assert.equal(canAccessAdminRoute('/functions/workspace/background', superAdmin), true);
assert.equal(canAccessAdminRoute('/orders/manage', superAdmin), true);
assert.equal(canAccessAdminRoute('/system/ai-models', superAdmin), true);
assert.equal(canAccessAdminRoute('/system/video-generation', superAdmin), true);

console.log('Capability route/menu policy checks passed.');
