package com.example.gasuschedule.presentation.onboarding

import com.example.gasuschedule.domain.model.ScheduleNetworkException
import com.example.gasuschedule.domain.model.StudyGroup
import com.example.gasuschedule.domain.usecase.ScheduleDiffer
import com.example.gasuschedule.domain.usecase.SyncScheduleUseCase
import com.example.gasuschedule.testutil.FakePreferences
import com.example.gasuschedule.testutil.FakeScheduleRepository
import com.example.gasuschedule.testutil.MainDispatcherRule
import com.example.gasuschedule.testutil.clockAt
import com.example.gasuschedule.testutil.d
import com.example.gasuschedule.testutil.lesson
import com.example.gasuschedule.testutil.schedule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class OnboardingViewModelTest {

    @get:Rule val main = MainDispatcherRule()

    private val catalog = listOf(
        StudyGroup("1-ТТП-26", "Автомобильно-дорожный факультет", "Бакалавриат", "1 курс"),
        StudyGroup("3-ТТП-26", "Автомобильно-дорожный факультет", "Бакалавриат", "1 курс"),
        StudyGroup("3-ТТП-25"),
        StudyGroup("3-ТТП-26/2"),
        StudyGroup("5-А-26"),
    )

    private val repo = FakeScheduleRepository().apply { groups = { catalog } }
    private val prefs = FakePreferences(group = null)
    private fun vm() = OnboardingViewModel(repo, prefs, SyncScheduleUseCase(repo, prefs, ScheduleDiffer(), clockAt(d(28))))

    @Test
    fun `фильтр - без учёта регистра и пробелов, точное совпадение первым`() {
        val names = { q: String -> OnboardingViewModel.filterGroups(catalog, q).map { it.name } }
        assertEquals(listOf("3-ТТП-26", "3-ТТП-26/2"), names("3-ттп-26"))
        assertEquals(listOf("3-ТТП-25", "3-ТТП-26", "3-ТТП-26/2"), names("3-ТТП"))
        assertEquals(listOf("1-ТТП-26", "3-ТТП-25", "3-ТТП-26", "3-ТТП-26/2"), names(" ттп "))
        assertEquals("латиница как транслит", listOf("3-ТТП-26", "3-ТТП-26/2"), names("3-ttp-26"))
        assertEquals(listOf("5-А-26"), names("5-a"))
        assertTrue(names("").isEmpty())
        assertTrue(names("xyz").isEmpty())
    }

    @Test
    fun `выбор группы и успешное продолжение сохраняют группу`() {
        repo.remote = { schedule(listOf(5), lesson(d(29), 3)) }
        val vm = vm()
        vm.onQueryChange("3-ттп")
        assertEquals(3, vm.state.value.matches.size)
        assertNull(vm.state.value.candidate)

        vm.select(catalog[1])
        assertEquals("3-ТТП-26", vm.state.value.candidate)
        vm.submit()

        assertTrue(vm.state.value.done)
        assertEquals("3-ТТП-26", prefs.groupName.value)
        assertEquals(1, repo.snapshot("3-ТТП-26").lessons.size)
    }

    @Test
    fun `сеть упала при загрузке расписания - группа не сохраняется, есть сообщение`() {
        repo.remote = { throw ScheduleNetworkException("нет сети") }
        val vm = vm()
        vm.select(catalog[1])
        vm.submit()
        assertFalse(vm.state.value.done)
        assertNull(prefs.groupName.value)
        assertTrue(vm.state.value.error!!.contains("нет связи"))
    }

    @Test
    fun `у группы нет пар - понятная ошибка`() {
        repo.remote = { schedule(listOf(5)) }
        val vm = vm()
        vm.select(catalog[4])
        vm.submit()
        assertEquals("На сайте нет пар для этой группы. Проверь название.", vm.state.value.error)
    }

    @Test
    fun `список групп не загрузился - можно продолжить с введённым вручную`() {
        repo.groups = { throw ScheduleNetworkException("нет сети") }
        val vm = vm()
        assertTrue(vm.state.value.groups is GroupsState.Error)
        vm.onQueryChange(" 3-ТТП-26 ")
        assertEquals("3-ТТП-26", vm.state.value.candidate)

        repo.groups = { catalog }
        vm.loadGroups()
        assertTrue(vm.state.value.groups is GroupsState.Loaded)
        assertNull("со списком — только выбор из списка", vm.state.value.candidate)
    }

    @Test
    fun `смена группы - текущая подставлена`() {
        prefs.groupName.value = "3-ТТП-26"
        val vm = vm()
        assertEquals("3-ТТП-26", vm.state.value.selected)
        assertEquals(listOf("3-ТТП-26", "3-ТТП-26/2"), vm.state.value.matches.map { it.name })
    }
}
