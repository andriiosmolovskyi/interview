package forex.services.rates

import forex.domain.{Currency, Pair}

package object interpreters {
  val AllPossiblePairs: List[Pair] = {
    val allCurrencies = Currency.values.toList

    allCurrencies.flatMap { firstCurrency =>
      allCurrencies.filter(_ != firstCurrency).map { secondCurrency =>
          Pair(firstCurrency, secondCurrency)
      }
    }
  }
}
