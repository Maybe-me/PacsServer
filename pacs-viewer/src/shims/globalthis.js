function getGlobalThis() {
  return globalThis
}

getGlobalThis.getPolyfill = () => globalThis
getGlobalThis.implementation = globalThis
getGlobalThis.shim = () => globalThis

export default getGlobalThis
