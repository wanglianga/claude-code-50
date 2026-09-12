import { createRouter, createWebHashHistory } from 'vue-router'
import { auth } from './store'

const routes = [
  { path: '/login', component: () => import('./views/Login.vue') },
  { path: '/', redirect: '/orders' },
  { path: '/new-order', component: () => import('./views/NewOrder.vue') },
  { path: '/orders', component: () => import('./views/Orders.vue') },
  { path: '/orders/:id', component: () => import('./views/OrderDetail.vue') },
  { path: '/drugs', component: () => import('./views/Drugs.vue') },
  { path: '/shifts', component: () => import('./views/Shifts.vue') },
  { path: '/complaints', component: () => import('./views/Complaints.vue') }
]

const router = createRouter({ history: createWebHashHistory(), routes })

router.beforeEach((to) => {
  if (to.path !== '/login' && !auth.user) return '/login'
  if (to.path === '/login' && auth.user) return '/orders'
})

export default router
